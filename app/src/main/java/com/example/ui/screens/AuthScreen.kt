package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Authentication is intentionally disabled in the offline/local account-free edition.
 * Any legacy navigation to this screen is immediately redirected to the app.
 */
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    LaunchedEffect(Unit) { onAuthSuccess() }
}
