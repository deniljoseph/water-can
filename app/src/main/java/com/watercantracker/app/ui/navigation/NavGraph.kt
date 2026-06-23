package com.watercantracker.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.watercantracker.app.ui.screens.dashboard.DashboardScreen
import com.watercantracker.app.ui.screens.members.AddEditMemberScreen
import com.watercantracker.app.ui.screens.members.MembersScreen
import com.watercantracker.app.ui.screens.payments.AddEditPaymentScreen
import com.watercantracker.app.ui.screens.payments.PaymentsScreen
import com.watercantracker.app.ui.screens.reports.ReportsScreen
import com.watercantracker.app.ui.screens.settings.SettingsScreen

@Composable
fun WaterCanNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(220)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(220)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(220)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(220)
            )
        }
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddPayment = { navController.navigate(Screen.AddPayment.route) }
            )
        }

        composable(Screen.Payments.route) {
            PaymentsScreen(
                onAddPayment = { navController.navigate(Screen.AddPayment.route) },
                onEditPayment = { id -> navController.navigate(Screen.EditPayment.createRoute(id)) }
            )
        }

        composable(Screen.AddPayment.route) {
            AddEditPaymentScreen(
                paymentId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditPayment.route,
            arguments = listOf(navArgument("paymentId") { type = NavType.LongType })
        ) { back ->
            AddEditPaymentScreen(
                paymentId = back.arguments?.getLong("paymentId"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Members.route) {
            MembersScreen(
                onAddMember = { navController.navigate(Screen.AddMember.route) },
                onEditMember = { id -> navController.navigate(Screen.EditMember.createRoute(id)) }
            )
        }

        composable(Screen.AddMember.route) {
            AddEditMemberScreen(
                memberId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditMember.route,
            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
        ) { back ->
            AddEditMemberScreen(
                memberId = back.arguments?.getLong("memberId"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Reports.route) {
            ReportsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
