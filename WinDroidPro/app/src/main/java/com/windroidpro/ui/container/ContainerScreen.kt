package com.windroidpro.ui.container

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.windroidpro.data.Container
import com.windroidpro.runtime.RuntimeProfile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(
    onBackClick: () -> Unit,
    onContainerClick: (Container) -> Unit,
    viewModel: ContainerViewModel = hiltViewModel()
) {
    val containers by viewModel.containers.collectAsState()
    val runtime by viewModel.runtime.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Container?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Containers") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (runtime.ready && !busy) showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Container")
            }
        }
    ) { paddingValues ->
        if (containers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!runtime.ready) "Install the Windows runtime from Home first." else "No containers yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(containers, key = { it.id }) { container ->
                    ContainerItem(
                        container = container,
                        onClick = { if (runtime.ready && !busy) viewModel.launch(container) { id ->
                            onContainerClick(container.copy(runtimeId = id))
                        } },
                        onDelete = { if (!busy) pendingDelete = container }
                    )
                }
            }
        }

        if (showCreateDialog) {
            CreateContainerDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, desc, profile ->
                    viewModel.createContainer(name, desc, profile)
                    showCreateDialog = false
                }
            )
        }
        if (busy) AlertDialog(onDismissRequest = {}, title = { Text("Preparing container…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
        error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError,
            title = { Text("Container error") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }) }
        pendingDelete?.let { selected ->
            AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete ${selected.name}?") },
                text = { Text("This removes its Windows settings and files installed on C:. Your imported game folders stay in the library.") },
                confirmButton = { TextButton(onClick = {
                    pendingDelete = null; viewModel.deleteContainer(selected)
                }) { Text("Delete") } }, dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerItem(
    container: Container,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = container.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (container.description.isNotEmpty()) {
                            Text(
                                text = container.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Wine ${container.wineVersion}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row {
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateContainerDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, RuntimeProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(RuntimeProfile.MALI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Container") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Starting profile", style = MaterialTheme.typography.titleSmall)
                RuntimeProfile.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = profile == option, onClick = { profile = option })
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, profile) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
