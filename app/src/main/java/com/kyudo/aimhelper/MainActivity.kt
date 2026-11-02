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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kyudo.aimhelper.ui.FormCheckScreen
import com.kyudo.aimhelper.ui.HomeScreen
import com.kyudo.aimhelper.ui.Ink
import com.kyudo.aimhelper.ui.KyudoTheme
import com.kyudo.aimhelper.ui.NeraiScreen
import com.kyudo.aimhelper.ui.ReviewScreen
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
                val nav = rememberNavController()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .safeDrawingPadding(),
                ) {
                    NavHost(navController = nav, startDestination = "home") {
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
                            ReviewScreen(vm = vm, onBack = { nav.popBackStack() })
                        }
                        composable("nerai") {
                            NeraiScreen(
                                vm = vm,
                                onBack = { nav.popBackStack() },
                                onStart = { nav.navigate("form") },
                            )
                        }
                    }
                }
            }
        }
    }
}
