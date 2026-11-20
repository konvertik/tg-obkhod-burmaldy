package ru.mtproxymanager.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mtproxymanager.data.ThemeMode
import ru.mtproxymanager.ui.theme.MtProxyTheme

enum class Screen(val title: String, val icon: ImageVector) {
    MAIN("MTProto прокси", Icons.Filled.Home),
    FAVORITES("Избранное", Icons.Filled.Favorite),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtProxyApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = when (state.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MtProxyTheme(darkTheme = darkTheme) {
        var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
        var showAdd by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.messages.collect { snackbar.showSnackbar(it) }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(screen.title, fontWeight = FontWeight.SemiBold) },
                    actions = {
                        if (screen == Screen.MAIN) {
                            IconButton(onClick = { showAdd = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Добавить прокси по ссылке")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.title) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val contentModifier = Modifier.padding(padding)
            when (screen) {
                Screen.MAIN -> MainScreen(state, viewModel, contentModifier)
                Screen.FAVORITES -> FavoritesScreen(state, viewModel, contentModifier)
                Screen.SETTINGS -> SettingsScreen(state, viewModel, contentModifier)
            }
        }

        if (showAdd) {
            AddProxyDialog(
                onDismiss = { showAdd = false },
                onSubmit = viewModel::addProxy,
            )
        }
    }
}
