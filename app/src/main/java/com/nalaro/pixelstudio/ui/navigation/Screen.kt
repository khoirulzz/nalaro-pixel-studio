package com.nalaro.pixelstudio.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Studio : Screen("studio?tool={tool}") {
        fun createRoute(tool: String = "compress") = "studio?tool=$tool"
    }
    object Batch : Screen("batch")
    object Library : Screen("library")
    object ModelManager : Screen("model_manager")
}
