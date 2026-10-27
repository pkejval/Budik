package io.github.pkejval.budik.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BudikTheme {
                val nav = rememberNavController()
                val idArg = listOf(navArgument("id") { type = NavType.LongType })
                NavHost(nav, startDestination = "alarms") {
                    composable("alarms") {
                        AlarmListScreen(
                            onEdit = { nav.navigate("alarm/$it") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("alarm/{id}", idArg) {
                        AlarmEditScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onIntegration = { id -> nav.navigate("integration/$id") },
                        )
                    }
                    composable("integration/{id}", idArg) {
                        IntegrationEditScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
