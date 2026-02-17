package io.github.climbintelligence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.climbintelligence.ui.screens.AboutScreen
import io.github.climbintelligence.ui.screens.AlertsScreen
import io.github.climbintelligence.ui.screens.AthleteScreen
import io.github.climbintelligence.ui.screens.BikeScreen
import io.github.climbintelligence.ui.screens.DetectionScreen
import io.github.climbintelligence.ui.screens.HistoryScreen
import io.github.climbintelligence.ui.screens.MainMenuScreen
import io.github.climbintelligence.ui.screens.PacingScreen
import io.github.climbintelligence.ui.theme.ClimbIntelligenceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClimbIntelligenceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainMenuScreen(
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }
                        composable("settings/athlete") {
                            AthleteScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings/bike") {
                            BikeScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings/detection") {
                            DetectionScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings/pacing") {
                            PacingScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings/alerts") {
                            AlertsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings/about") {
                            AboutScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("history") {
                            HistoryScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
