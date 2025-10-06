package com.zybooks.cs360project2updated.navigation

// Sealed class to manage different navigation states in app
sealed class NavigationState {
    // Represents login screen
    object Login : NavigationState()
    // Represents sign-up screen
    object SignUp : NavigationState()
    // Represents grid screen
    object Grid : NavigationState()
    // Represents add weight screen
    object AddWeight : NavigationState()
    // Represents SMS screen
    object SMS : NavigationState()
}