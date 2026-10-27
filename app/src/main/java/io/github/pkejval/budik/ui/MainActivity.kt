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
import io.github.pkejval.budik.data.IntegrationType

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
                            onIntegration = { id, type -> nav.navigate("integration/$id?type=${type?.name ?: ""}") },
                        )
                    }
                    composable(
                        "integration/{id}?type={type}",
                        idArg + navArgument("type") { type = NavType.StringType; defaultValue = "" },
                    ) {
                        val type = IntegrationType.entries.firstOrNull { t -> t.name == it.arguments?.getString("type") }
                        IntegrationEditScreen(it.arguments!!.getLong("id"), type, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
