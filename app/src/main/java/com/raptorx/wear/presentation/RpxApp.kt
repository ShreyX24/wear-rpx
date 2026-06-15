package com.raptorx.wear.presentation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.raptorx.wear.presentation.screens.ConnectScreen
import com.raptorx.wear.presentation.screens.RunDetailScreen
import com.raptorx.wear.presentation.screens.RunListScreen
import com.raptorx.wear.presentation.theme.RpxTheme

object Routes {
    const val RUNS = "runs"
    const val DETAIL = "detail"
    const val CONNECT = "connect"
}

@Composable
fun RpxApp(viewModel: RpxViewModel = viewModel()) {
    val navController = rememberSwipeDismissableNavController()
    RpxTheme {
        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = Routes.RUNS,
            ) {
                composable(Routes.RUNS) {
                    RunListScreen(
                        viewModel = viewModel,
                        onOpenRun = { runId ->
                            viewModel.openRun(runId)
                            navController.navigate(Routes.DETAIL)
                        },
                        onConnect = { navController.navigate(Routes.CONNECT) },
                    )
                }
                composable(Routes.DETAIL) {
                    RunDetailScreen(viewModel = viewModel)
                }
                composable(Routes.CONNECT) {
                    ConnectScreen(
                        viewModel = viewModel,
                        onConnected = {
                            if (!navController.popBackStack(Routes.RUNS, inclusive = false)) {
                                navController.navigate(Routes.RUNS) {
                                    popUpTo(Routes.CONNECT) { inclusive = true }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
