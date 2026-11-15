package dev.oni.dreaminstall.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.oni.dreaminstall.AppViewModel
import dev.oni.dreaminstall.R
import dev.oni.dreaminstall.ui.theme.DreamTheme
import dev.oni.dreaminstall.ui.theme.LocalPalette

@Composable
fun DreamInstallApp(vm: AppViewModel) {
    val accent by vm.accent.collectAsStateWithLifecycle()
    DreamTheme(accent) {
        val palette = LocalPalette.current
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val snackbar = remember { SnackbarHostState() }
        val message by vm.message.collectAsStateWithLifecycle()

        LaunchedEffect(message) {
            val text = message
            if (text != null) {
                snackbar.showSnackbar(text)
                vm.consumeMessage()
            }
        }

        Scaffold(
            containerColor = Color(palette.bg),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color(palette.card), tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_install)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(palette.accent),
                            selectedTextColor = Color(palette.accent),
                            indicatorColor = Color(palette.selected),
                            unselectedIconColor = Color(palette.dim),
                            unselectedTextColor = Color(palette.dim),
                        ),
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(palette.accent),
                            selectedTextColor = Color(palette.accent),
                            indicatorColor = Color(palette.selected),
                            unselectedIconColor = Color(palette.dim),
                            unselectedTextColor = Color(palette.dim),
                        ),
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (tab == 0) {
                    InstallScreen(vm)
                } else {
                    SettingsScreen(vm)
                }
            }
        }
    }
}
