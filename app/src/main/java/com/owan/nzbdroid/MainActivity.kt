package com.owan.nzbdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.owan.nzbdroid.ui.files.FilesScreen
import com.owan.nzbdroid.ui.queue.QueueScreen
import com.owan.nzbdroid.ui.search.SearchScreen
import com.owan.nzbdroid.ui.settings.IndexersSettingsScreen
import com.owan.nzbdroid.ui.settings.NzbGetSettingsScreen
import com.owan.nzbdroid.ui.settings.SettingsHubScreen
import com.owan.nzbdroid.ui.settings.SmbSettingsScreen
import com.owan.nzbdroid.ui.theme.NzbDroidTheme

private sealed class Tab(val route: String, val label: String) {
    data object Queue : Tab("queue", "Queue")
    data object Search : Tab("search", "Search")
    data object Files : Tab("files", "Files")
    data object Settings : Tab("settings", "Settings")
}

private val tabs = listOf(Tab.Queue, Tab.Search, Tab.Files, Tab.Settings)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NzbDroidApp).container

        setContent {
            NzbDroidTheme {
                val navController = rememberNavController()

                Scaffold(
                    bottomBar = {
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = backStackEntry?.destination

                        NavigationBar {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        val icon = when (tab) {
                                            Tab.Queue -> Icons.Filled.CloudDownload
                                            Tab.Search -> Icons.Filled.Search
                                            Tab.Files -> Icons.Filled.Folder
                                            Tab.Settings -> Icons.Filled.Settings
                                        }
                                        Icon(icon, contentDescription = tab.label)
                                    },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Tab.Queue.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Tab.Queue.route) {
                            QueueScreen(container)
                        }
                        composable(Tab.Search.route) {
                            SearchScreen(container)
                        }
                        composable(Tab.Files.route) {
                            FilesScreen(container)
                        }
                        composable(Tab.Settings.route) {
                            SettingsHubScreen(
                                onOpenNzbGet = { navController.navigate("settings/nzbget") },
                                onOpenIndexers = { navController.navigate("settings/indexers") },
                                onOpenSmb = { navController.navigate("settings/smb") }
                            )
                        }
                        composable("settings/nzbget") {
                            NzbGetSettingsScreen(container, onBack = { navController.popBackStack() })
                        }
                        composable("settings/indexers") {
                            IndexersSettingsScreen(container, onBack = { navController.popBackStack() })
                        }
                        composable("settings/smb") {
                            SmbSettingsScreen(container, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
