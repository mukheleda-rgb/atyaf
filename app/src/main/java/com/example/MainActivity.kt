package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        val wallpaperRepository = WallpaperRepository(this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(wallpaperRepository)
                }
            }
        }
    }
}

@Composable
fun AppRoot(repository: WallpaperRepository) {
    val auth = Firebase.auth
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        MainAppScreen(repository = repository, onSignOut = { currentUser = null })
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "الرئيسية", Icons.Default.Home)
    object AiGenerator : Screen("ai", "مولد الذكاء", Icons.Default.AutoAwesome)
    object Blend : Screen("blend", "دمج الخلفيات", Icons.Default.Layers)
    object Favorites : Screen("favorites", "المفضلة", Icons.Default.Favorite)
    object Settings : Screen("settings", "الإعدادات", Icons.Default.Settings)
}

@Composable
fun MainAppScreen(repository: WallpaperRepository, onSignOut: () -> Unit) {
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var previewWallpaper by remember { mutableStateOf<WallpaperItem?>(null) }
    var curatedList by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        curatedList = repository.fetchRealWallpapers("الكل")
    }

    val screens = listOf(Screen.Home, Screen.AiGenerator, Screen.Blend, Screen.Favorites, Screen.Settings)

    if (previewWallpaper != null) {
        PreviewScreen(
            item = previewWallpaper!!,
            repository = repository,
            onBack = { previewWallpaper = null }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedScreen) {
                    Screen.Home -> HomeScreen(repository = repository, onWallpaperClick = { previewWallpaper = it })
                    Screen.AiGenerator -> AiGeneratorScreen(repository = repository)
                    Screen.Blend -> BlendScreen(repository = repository, wallpapers = curatedList)
                    Screen.Favorites -> FavoritesScreen(repository = repository, onWallpaperClick = { previewWallpaper = it })
                    Screen.Settings -> SettingsScreen(onSignOut = onSignOut)
                }
            }
        }
    }
}

// App Check Helper
private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit().putString(DEBUG_SECRET_KEY, debugToken).commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
