package com.windroidpro.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.windroidpro.R
import com.windroidpro.ui.container.ContainerActivity
import com.windroidpro.ui.theme.WinDroidProTheme
import com.windroidpro.ui.usb.UsbDevicesScreen
import timber.log.Timber
import androidx.hilt.navigation.compose.hiltViewModel
import com.windroidpro.runtime.RuntimeLauncher
import com.windroidpro.ui.library.GameLibraryActivity

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("MainActivity created")
        
        setContent {
            WinDroidProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object UsbDevices : Screen("usb_devices")
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            MainScreen(
                onNavigateToUsbDevices = {
                    navController.navigate(Screen.UsbDevices.route)
                }
            )
        }
        composable(Screen.UsbDevices.route) {
            UsbDevicesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToUsbDevices: () -> Unit,
    viewModel: RuntimeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val runtime by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(id = R.string.welcome_message, stringResource(id = R.string.app_name)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(id = R.string.app_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(runtime.message, style = MaterialTheme.typography.titleMedium)
                    if (runtime.installing) LinearProgressIndicator(
                        progress = runtime.progress / 100f,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    if (!runtime.ready && !runtime.installing) {
                        Text("Setup installs the bundled Windows runtime. Keep this app open until it finishes.", Modifier.padding(vertical = 8.dp))
                        Button(onClick = viewModel::install) { Text("Install runtime") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = Intent(context, ContainerActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(), enabled = runtime.ready
            ) {
                Text("Manage Containers")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { context.startActivity(Intent(context, GameLibraryActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(), enabled = runtime.ready) { Text("Game library") }
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onNavigateToUsbDevices,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("USB Devices")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    RuntimeLauncher.settings(context)
                },
                modifier = Modifier.fillMaxWidth(), enabled = runtime.ready
            ) {
                Text("Runtime settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { RuntimeLauncher.settings(context, com.winlator.R.id.menu_item_input_controls) },
                modifier = Modifier.fillMaxWidth(), enabled = runtime.ready) { Text("Touch and controller layouts") }
        }
    }
}
