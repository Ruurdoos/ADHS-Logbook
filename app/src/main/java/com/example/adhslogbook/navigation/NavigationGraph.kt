package com.example.adhslogbook.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.adhslogbook.ui.screens.insights.InsightsRoute
import com.example.adhslogbook.ui.screens.timeline.TimelineRoute
import com.example.adhslogbook.ui.screens.today.TodayRoute

enum class FocusLogDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Today("today", "Today", Icons.Outlined.CalendarToday),
    Timeline("timeline", "Timeline", Icons.Outlined.Timeline),
    Insights("insights", "Insights", Icons.Outlined.Analytics),
}

val ProductDestinations = listOf(
    FocusLogDestination.Today,
    FocusLogDestination.Timeline,
    FocusLogDestination.Insights,
)

@Composable
fun NavigationGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = FocusLogDestination.Today.route,
    ) {
        composable(FocusLogDestination.Today.route) {
            TodayRoute(
                currentDestination = FocusLogDestination.Today,
                onNavigate = navController::navigateToTopLevel,
                onHomeClick = { navController.navigateToTopLevel(FocusLogDestination.Today) },
            )
        }
        composable(FocusLogDestination.Timeline.route) {
            TimelineRoute(
                currentDestination = FocusLogDestination.Timeline,
                onNavigate = navController::navigateToTopLevel,
                onHomeClick = { navController.navigateToTopLevel(FocusLogDestination.Today) },
            )
        }
        composable(FocusLogDestination.Insights.route) {
            InsightsRoute(
                currentDestination = FocusLogDestination.Insights,
                onNavigate = navController::navigateToTopLevel,
                onHomeClick = { navController.navigateToTopLevel(FocusLogDestination.Today) },
            )
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: FocusLogDestination) {
    navigate(destination.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
