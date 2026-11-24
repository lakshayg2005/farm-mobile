package com.farm.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-wide "something changed on the server" signal. A push (an order moved on, a route was
 * offered) calls [changed]; every screen that loads data keys its load on [rememberRefreshTick],
 * so all open screens re-read at once instead of only the one the person pulled down.
 */
object RefreshSignal {
    private val _ticks = MutableStateFlow(0)
    val ticks: StateFlow<Int> = _ticks.asStateFlow()

    fun changed() = _ticks.update { it + 1 }
}

/**
 * A number that goes up when [RefreshSignal.changed] fires or when the screen comes back to the
 * foreground (the person may have been away while statuses changed). Put it in a load's
 * LaunchedEffect keys.
 */
@Composable
fun rememberRefreshTick(): Int {
    val signal by RefreshSignal.ticks.collectAsState()
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else resumes++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return signal + resumes
}

/**
 * Pull down to reload. The content must scroll (a LazyColumn, or a Column with verticalScroll)
 * for the gesture to reach the indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier, content = content)
}
