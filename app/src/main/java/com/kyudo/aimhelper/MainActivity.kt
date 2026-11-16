package com.kyudo.aimhelper

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kyudo.aimhelper.ui.FormCheckScreen
import com.kyudo.aimhelper.ui.GuideScreen
import com.kyudo.aimhelper.ui.HomeScreen
import com.kyudo.aimhelper.ui.Ink
import com.kyudo.aimhelper.ui.KyudoTheme
import com.kyudo.aimhelper.ui.LocalShowKanji
import com.kyudo.aimhelper.ui.NeraiScreen
import com.kyudo.aimhelper.ui.ReviewScreen
import com.kyudo.aimhelper.ui.ShotDetailScreen
import com.kyudo.aimhelper.ui.ShotLogScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            KyudoTheme {
                val vm: AppViewModel = viewModel()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val nav = rememberNavController()
                // Decided once: the guide opens by itself only the first time.
                val start = remember { if (settings.seenGuide) "home" else "welcome" }

                CompositionLocalProvider(LocalShowKanji provides settings.showKanji) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Ink)
                            .safeDrawingPadding(),
                    ) {
                        NavHost(navController = nav, startDestination = start) {
                            composable("welcome") {
                                GuideScreen(onDone = {
                                    vm.markGuideSeen()
                                    nav.navigate("home") { popUpTo("welcome") { inclusive = true } }
                                })
                            }
                            composable("guide") {
                                GuideScreen(onDone = { nav.popBackStack() })
                            }
                            composable("home") {
                                HomeScreen(
                                    vm = vm,
                                    onStart = {
                                        vm.startSession()
                                        nav.navigate("form")
                                    },
                                    onOpen = { route -> nav.navigate(route) },
                                )
                            }
                            composable("form") {
                                FormCheckScreen(
                                    vm = vm,
                                    onBack = { nav.popBackStack() },
                                    onLog = { nav.navigate("log") },
                                    onHelp = { nav.navigate("guide") },
                                )
                            }
                            composable("log") {
                                ShotLogScreen(
                                    vm = vm,
                                    onBack = { nav.popBackStack() },
                                    onFinish = {
                                        vm.endSession()
                                        nav.navigate("review") { popUpTo("home") }
                                    },
                                )
                            }
                            composable("review") {
                                ReviewScreen(
                                    vm = vm,
                                    onBack = { nav.popBackStack() },
                                    onOpenShot = { sessionId, shotId -> nav.navigate("shot/$sessionId/$shotId") },
                                )
                            }
                            composable(
                                "shot/{sessionId}/{shotId}",
                                arguments = listOf(
                                    navArgument("sessionId") { type = NavType.LongType },
                                    navArgument("shotId") { type = NavType.LongType },
                                ),
                            ) { entry ->
                                ShotDetailScreen(
                                    vm = vm,
                                    sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                                    shotId = entry.arguments?.getLong("shotId") ?: 0L,
                                    onBack = { nav.popBackStack() },
                                )
                            }
                            composable("nerai") {
                                NeraiScreen(
                                    vm = vm,
                                    onBack = { nav.popBackStack() },
                                    onStart = { nav.navigate("form") },
                                    onGuide = { nav.navigate("guide") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
