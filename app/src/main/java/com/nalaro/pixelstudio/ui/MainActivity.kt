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
import androidx.navigation.compose.*
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

        // Read incoming image Uri from Android Sharesheet if present
        var sharedImageUri: Uri? = null
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            sharedImageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        setContent {
            NalaroPixelStudioTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                var selectedTab by remember { mutableStateOf(AppTab.HOME) }

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

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            BottomNavigationCompact(
                                currentTab = selectedTab,
                                onTabSelected = { tab ->
                                    selectedTab = tab
                                    when (tab) {
                                        AppTab.HOME -> navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Home.route) { inclusive = true }
                                        }
                                        AppTab.STUDIO -> navController.navigate(Screen.Studio.createRoute())
                                        AppTab.BATCH -> navController.navigate(Screen.Batch.route)
                                        AppTab.LIBRARY -> navController.navigate(Screen.Library.route)
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
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToStudio = { tool ->
                                    navController.navigate(Screen.Studio.createRoute(tool))
                                },
                                onNavigateToModelManager = {
                                    navController.navigate(Screen.ModelManager.route)
                                }
                            )
                        }

                        composable(Screen.Studio.route) { backStackEntry ->
                            val tool = backStackEntry.arguments?.getString("tool") ?: "compress"
                            StudioScreen(
                                initialTool = tool,
                                modelRepository = repository,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.Batch.route) {
                            BatchScreen()
                        }

                        composable(Screen.Library.route) {
                            LibraryScreen()
                        }

                        composable(Screen.ModelManager.route) {
                            ModelManagerScreen(
                                repository = repository,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
