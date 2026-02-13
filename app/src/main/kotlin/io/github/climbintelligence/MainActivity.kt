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
import io.github.climbintelligence.ui.screens.SettingsScreen
import io.github.climbintelligence.ui.screens.HistoryScreen
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
                        startDestination = "settings"
                    ) {
                        composable("settings") {
                            SettingsScreen(
                                onNavigateToHistory = {
                                    navController.navigate("history")
                                }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
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
