package com.nalaro.pixelstudio.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nalaro.pixelstudio.data.repository.ModelRepository
import com.nalaro.pixelstudio.ui.components.AppTab
import com.nalaro.pixelstudio.ui.components.BottomNavigationCompact
import com.nalaro.pixelstudio.ui.navigation.Screen
import com.nalaro.pixelstudio.ui.screens.batch.BatchScreen
import com.nalaro.pixelstudio.ui.screens.home.HomeScreen
import com.nalaro.pixelstudio.ui.screens.library.LibraryScreen
import com.nalaro.pixelstudio.ui.screens.modelmanager.ModelManagerScreen
import com.nalaro.pixelstudio.ui.screens.studio.StudioScreen
import com.nalaro.pixelstudio.ui.theme.NalaroPixelStudioTheme
import com.nalaro.pixelstudio.ui.theme.SurfaceBase

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ModelRepository.getInstance(applicationContext)

        // Read incoming image Uri(s) from Android Sharesheet if present
        val sharedImageUri: Uri? = when {
            intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            }
            intent?.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
            }
            else -> null
        }

        val initialStartDestination = if (sharedImageUri != null) {
            Screen.Studio.route
        } else {
            Screen.Home.route
        }

        setContent {
            NalaroPixelStudioTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                var selectedTab by remember {
                    mutableStateOf(if (sharedImageUri != null) AppTab.STUDIO else AppTab.HOME)
                }

                // Synchronize tab with route
                LaunchedEffect(currentRoute) {
                    when {
                        currentRoute == Screen.Home.route -> selectedTab = AppTab.HOME
                        currentRoute?.startsWith("studio") == true -> selectedTab = AppTab.STUDIO
                        currentRoute == Screen.Batch.route -> selectedTab = AppTab.BATCH
                        currentRoute == Screen.Library.route -> selectedTab = AppTab.LIBRARY
                    }
                }

                val showBottomNav = currentRoute != Screen.ModelManager.route

                val navigateBackSafely: () -> Unit = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            launchSingleTop = true
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            BottomNavigationCompact(
                                currentTab = selectedTab,
                                onTabSelected = { tab ->
                                    selectedTab = tab
                                    val targetRoute = when (tab) {
                                        AppTab.HOME -> Screen.Home.route
                                        AppTab.STUDIO -> Screen.Studio.createRoute("compress")
                                        AppTab.BATCH -> Screen.Batch.route
                                        AppTab.LIBRARY -> Screen.Library.route
                                    }
                                    navController.navigate(targetRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    },
                    containerColor = SurfaceBase,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = initialStartDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToStudio = { tool ->
                                    navController.navigate(Screen.Studio.createRoute(tool)) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToModelManager = {
                                    navController.navigate(Screen.ModelManager.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(
                            route = Screen.Studio.route,
                            arguments = listOf(
                                navArgument("tool") {
                                    type = NavType.StringType
                                    defaultValue = "compress"
                                }
                            )
                        ) { backStackEntry ->
                            val tool = backStackEntry.arguments?.getString("tool") ?: "compress"
                            StudioScreen(
                                initialTool = tool,
                                initialImageUri = sharedImageUri,
                                modelRepository = repository,
                                onNavigateBack = navigateBackSafely
                            )
                        }

                        composable(Screen.Batch.route) {
                            BatchScreen(
                                onNavigateBack = navigateBackSafely
                            )
                        }

                        composable(Screen.Library.route) {
                            LibraryScreen()
                        }

                        composable(Screen.ModelManager.route) {
                            ModelManagerScreen(
                                repository = repository,
                                onNavigateBack = navigateBackSafely
                            )
                        }
                    }
                }
            }
        }
    }
}
