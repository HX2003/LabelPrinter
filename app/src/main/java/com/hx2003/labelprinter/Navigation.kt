package com.hx2003.labelprinter

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.hx2003.labelprinter.screens.HomeScreen
import com.hx2003.labelprinter.screens.PreviewScreen
import com.hx2003.labelprinter.ui.theme.AnimationDuration

import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

sealed class Screen: NavKey {
    @Serializable
    data object Home : Screen()

    @Serializable
    data object Preview : Screen()
}

@Composable
fun Navigation(modifier: Modifier = Modifier) {
    var useSpecialPrintAnimation by remember { mutableStateOf(false) }

    // We create printerViewModel: PrinterViewModel = koinViewModel() here
    // Since we want to scope printerViewModel to this composable instead of
    // declaring it inside HomeScreen or PreviewScreen.
    // We don't want to have 2 instances of printerViewModel for each screen,
    // since some states must be shared between them
    val printerApplicationViewModel: PrinterApplicationViewModel = koinViewModel()

    val backStack = rememberNavBackStack(Screen.Home)
    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        entryDecorators = listOf(
            rememberSavedStateNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberSceneSetupNavEntryDecorator()
        ),
        entryProvider = { key ->
            when (key) {
                is Screen.Home -> {
                    NavEntry(key) {
                        HomeScreen(
                            onDone = {
                                addUntil(backStack, Screen.Preview)
                            },
                            printerApplicationViewModel = printerApplicationViewModel
                        )
                    }
                }
                is Screen.Preview -> {
                    NavEntry(key, metadata = NavDisplay.popTransitionSpec {
                        if (useSpecialPrintAnimation) {
                            // Slide current content down, revealing the previous content in place underneath
                            EnterTransition.None togetherWith
                                slideOutVertically(targetOffsetY = { -it/4 }, animationSpec = tween(AnimationDuration)) +
                                fadeOut(animationSpec = tween(AnimationDuration)) +
                                scaleOut(animationSpec = tween(AnimationDuration))
                        } else {
                            // Slide horizontally when navigating back
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        }
                    }) {
                        PreviewScreen(
                            onBack = {
                                useSpecialPrintAnimation = false
                                removeUntil(backStack, Screen.Home)
                            },
                            onDone = {
                                useSpecialPrintAnimation = true
                                removeUntil(backStack, Screen.Home)
                            },
                            printerApplicationViewModel = printerApplicationViewModel
                        )
                    }
                }
                else -> {
                    error("Invalid navKey: $key")
                }
            }
        },
        transitionSpec = {
            fadeIn(animationSpec = tween(AnimationDuration)) togetherWith fadeOut(
                animationSpec = tween(AnimationDuration)
            )
        },
        popTransitionSpec = {
            fadeIn(animationSpec = tween(AnimationDuration)) togetherWith fadeOut(
                animationSpec = tween(AnimationDuration)
            )
        },
        predictivePopTransitionSpec = {
            fadeIn(animationSpec = tween(AnimationDuration)) togetherWith fadeOut(
                animationSpec = tween(AnimationDuration)
            )
        },
    )
}


// Clicking a button multiple times in quick succession
// before the screen animation has completed,
// will result in removeLastOrNull being called multiple times
// https://issuetracker.google.com/issues/426351559
// https://issuetracker.google.com/issues/430770579
//
// This results in unintended behaviour,
// so until there is some other solution,
// I created removeUntil function,
// which will remove elements until we reach the desired key
//
// I also created addUntil function,
// which will add element if and only if the last element is not that element
fun removeUntil(backStack: NavBackStack, key: NavKey) {
    while (backStack.isNotEmpty() && backStack.last() != key) {
        backStack.removeLastOrNull()
    }
}

fun addUntil(backStack: NavBackStack, key: NavKey) {
    if (backStack.lastOrNull() != key) {
        backStack.add(key)
    }
}
