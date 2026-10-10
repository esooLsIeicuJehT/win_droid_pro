package com.windroidpro.ui.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.windroidpro.data.Container
import com.windroidpro.runtime.RuntimeLauncher
import com.windroidpro.ui.theme.WinDroidProTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

@AndroidEntryPoint
class GameLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WinDroidProTheme { GameLibraryScreen(onBack = { finish() },
            onLaunch = { file, id -> RuntimeLauncher.executable(this, id, file) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameLibraryScreen(onBack: () -> Unit, onLaunch: (File, Int) -> Unit,
    viewModel: GameLibraryViewModel = hiltViewModel()) {
    val files by viewModel.executables.collectAsState()
    val containers by viewModel.containers.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val runtime by viewModel.runtime.collectAsState()
    var pending by remember { mutableStateOf<File?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) viewModel.importFolder(it)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) viewModel.importFiles(it)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Game library") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Import a complete, extracted game folder to keep its supporting files. New containers use D: for imported games.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { folderPicker.launch(null) }, enabled = !busy) { Text("Import folder") }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Import files") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            message?.let { Text(it, Modifier.padding(vertical = 12.dp)) }
            if (files.isEmpty() && !busy) Text("No Windows executables yet. Import a game or installer to get started.", Modifier.padding(top = 24.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.absolutePath }) { file ->
                    Card(onClick = { if (!busy && runtime.ready) pending = file }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium)
                            Text(file.relativeTo(viewModel.gamesDirectory).path,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    pending?.let { file ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Run ${file.name}") },
            text = {
                Column {
                    if (containers.isEmpty()) Text("Create a Windows container from Home first.")
                    containers.forEach { container: Container ->
                        TextButton(onClick = {
                            pending = null
                            viewModel.launch(file, container) { id -> onLaunch(file, id) }
                        }) { Text(container.name) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
}
