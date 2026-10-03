package com.example.autosim.utils

object GlobalSettings {
    var globalLoopMode: Int = 0 // 0: Once, 1: N Times, 2: Infinite
    var globalLoopCount: Int = 1
    var globalSequenceDelay: Long = 500L // Default delay between sequences in global runner
    
    // Profile Settings
    var currentProfileId: kotlinx.coroutines.flow.MutableStateFlow<Int> = kotlinx.coroutines.flow.MutableStateFlow(1)
    
    // Experimental Settings
    var detectActionsWithScreenCapture: Boolean = false
}
