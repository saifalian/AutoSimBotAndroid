package com.example.autosim

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.autosim.accessibility.AutomationAccessibilityService
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.ClickSpot
import com.example.autosim.engine.ScreenCaptureService
import com.example.autosim.ui.tabs.AddEditClickSpotScreen
import com.example.autosim.ui.tabs.ClickSpotsScreen
import com.example.autosim.ui.tabs.LogsScreen
import com.example.autosim.ui.tabs.OcrRegionsScreen
import com.example.autosim.ui.tabs.ImageTemplatesScreen
import com.example.autosim.ui.tabs.GlobalRunnerScreen
import com.example.autosim.ui.tabs.SequenceBuilderScreen
import com.example.autosim.ui.tabs.SettingsScreen
import com.example.autosim.ui.tabs.TextDetectionScreen
import com.example.autosim.ui.tabs.SwipesScreen
import com.example.autosim.ui.tabs.ProfileScreen
import com.example.autosim.ui.tabs.MacrosScreen
import com.example.autosim.ui.theme.AutoSimTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

sealed class Screen(val route: String, val resourceId: String, @DrawableRes val icon: Int) {
    object ClickSpots : Screen("click_spots", "Click Spots", R.drawable.ic_click_spot)
    object Swipes : Screen("swipes", "Swipes", R.drawable.ic_swipe)
    object OcrRegions : Screen("ocr_regions", "OCR Regions", R.drawable.ic_ocr_region)
    object TextDetection : Screen("text_detection", "Text Detection", R.drawable.ic_text_detection)
    object ImageRegion : Screen("image_region", "Image Region", R.drawable.ic_image)
    object ImageGlobal : Screen("image_global", "Image Global", R.drawable.ic_image)
    object VisualMacros : Screen("visual_macros", "Visual Macros", R.drawable.ic_record)
    object Macros : Screen("macros", "Macros", R.drawable.ic_record)
    object SequenceBuilder : Screen("sequence_builder", "Sequence Builder", R.drawable.ic_sequence_builder)
    object GlobalRunner : Screen("global_runner", "Playlist", R.drawable.ic_sequence_builder)
    object Logs : Screen("logs", "Logs", R.drawable.ic_logs)
    object Settings : Screen("settings", "Settings", R.drawable.ic_settings)
    object Profile : Screen("profile", "Profile", R.drawable.ic_settings)
}

object Routes {
    const val ADD_EDIT_CLICK_SPOT = "add_edit_click_spot"
}

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val requestProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val data = result.data!!
                val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = mediaProjectionManager.getMediaProjection(result.resultCode, data)
                if (projection != null) {
                    ScreenCaptureService.setMediaProjection(projection)
                }
            }
        }

    
    private val screenCaptureReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.autosim.REQUEST_SCREEN_CAPTURE") {
                startScreenCaptureIntent()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Register broadcast receiver
        val filter = android.content.IntentFilter("com.example.autosim.REQUEST_SCREEN_CAPTURE")
        registerReceiver(screenCaptureReceiver, filter, Context.RECEIVER_EXPORTED)
        
        // Initialize Profile
        val db = AppDatabase.getDatabase(this)
        val prefs = getSharedPreferences("autosim_prefs", Context.MODE_PRIVATE)
        
        lifecycleScope.launch {
            // 1. Ensure Default Profile exists
            val allProfiles = db.profileDao().getAll().first()
            var defaultProfile = allProfiles.firstOrNull { it.isDefault }
            if (defaultProfile == null) {
                val newProfile = com.example.autosim.db.Profile(name = "Default Profile", isDefault = true)
                val id = db.profileDao().insert(newProfile)
                defaultProfile = newProfile.copy(id = id.toInt())
            }
            
            // 2. Load last used profile ID
            val lastProfileId = prefs.getInt("last_profile_id", defaultProfile!!.id)
            
            // 3. Verify last profile  still exists
            val profile = db.profileDao().getProfileById(lastProfileId)
            if (profile != null) {
                com.example.autosim.utils.GlobalSettings.currentProfileId.value = profile.id
            } else {
                com.example.autosim.utils.GlobalSettings.currentProfileId.value = defaultProfile!!.id
                prefs.edit().putInt("last_profile_id", defaultProfile!!.id).apply()
            }
            
            // 4. Listen for profile changes to save to prefs
            com.example.autosim.utils.GlobalSettings.currentProfileId.collect { newId ->
                prefs.edit().putInt("last_profile_id", newId).apply()
            }
        }
        
        setContent {
            AutoSimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRequestScreenCapture = { startScreenCaptureIntent() },
                        onOpenAccessibilitySettings = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(intent)
                        },
                        onOpenOverlaySettings = { requestOverlayPermission() }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenCaptureReceiver)
    }

    fun startScreenCaptureIntent() {
        // Start the service BEFORE requesting permission (Android 14+ requirement)
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        requestProjection.launch(captureIntent)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            val svc = Intent(this, com.example.autosim.overlay.ExecutionOverlayService::class.java)
            ContextCompat.startForegroundService(this, svc)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${packageName}/${AutomationAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName) == true
    }
}

@Composable
fun MainScreen(
    onRequestScreenCapture: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.ClickSpots,
        Screen.Swipes,
        Screen.OcrRegions,
        Screen.TextDetection,
        Screen.ImageRegion,
        Screen.ImageGlobal,
        Screen.VisualMacros,
        Screen.Macros,
        Screen.SequenceBuilder,
        Screen.GlobalRunner,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(80.dp), // Increased height for better touch targets
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(painterResource(id = screen.icon), contentDescription = screen.resourceId) },
                            label = { Text(screen.resourceId, maxLines = 1, softWrap = false) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = false // Don't restore state to always go to main screen
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp) // Add spacing between items
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.ClickSpots.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.ClickSpots.route) { ClickSpotsScreen(navController = navController) }
            composable(Screen.Swipes.route) { SwipesScreen() }
            composable(Screen.OcrRegions.route) { OcrRegionsScreen() }
            composable(Screen.TextDetection.route) { TextDetectionScreen() }
            composable(Screen.ImageRegion.route) { ImageTemplatesScreen(regionOnly = true) }
            composable(Screen.ImageGlobal.route) { ImageTemplatesScreen(regionOnly = false) }
            composable(Screen.Macros.route) { MacrosScreen() }
            composable(Screen.SequenceBuilder.route) { SequenceBuilderScreen() }
            composable(Screen.GlobalRunner.route) { GlobalRunnerScreen() }
            composable(Screen.Logs.route) { LogsScreen() }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
            composable(Screen.Profile.route) { ProfileScreen() }
            composable(Screen.VisualMacros.route) { com.example.autosim.ui.tabs.VisualMacrosScreen() }

            composable(Routes.ADD_EDIT_CLICK_SPOT) {
                AddEditClickSpotScreen(navController = navController)
            }
            composable("${Routes.ADD_EDIT_CLICK_SPOT}/{spotId}") { backStackEntry ->
                val spotId = backStackEntry.arguments?.getString("spotId")?.toIntOrNull()
                AddEditClickSpotScreen(navController = navController, spotId = spotId)
            }
        }
    }
}

@Composable
fun AccessibilityPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accessibility Permission Needed") },
        text = { Text("To perform clicks and other actions, you need to enable the accessibility service for this app.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

