package com.example.ai_notetaker.ui.screen

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ai_notetaker.data.image.ImageHelper
import com.example.ai_notetaker.data.model.EntryType
import com.example.ai_notetaker.ui.component.FullSizeImageDialog
import com.example.ai_notetaker.ui.component.NoteEntryItem
import com.example.ai_notetaker.ui.viewmodel.NoteViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.detailUiState.collectAsState()
    val context = LocalContext.current
    val audioPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.RECORD_AUDIO)
    )
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA)
    )
    
    var imageToShow by remember { mutableStateOf<String?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageFile by remember { mutableStateOf<File?>(null) }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageFile != null) {
            // Save the image and add entry
            val imageFile = tempImageFile!!
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                bitmap?.let {
                    val savedPath = ImageHelper.saveImageFile(context, it, noteId)
                    viewModel.addImageEntry(savedPath)
                    // Delete temporary file
                    imageFile.delete()
                }
            }
            tempImageFile = null
            tempImageUri = null
        }
    }
    
    LaunchedEffect(noteId) {
        viewModel.loadNoteDetail(noteId)
    }
    
    // Clear state when navigating away
    DisposableEffect(noteId) {
        onDispose {
            viewModel.clearNoteDetail()
        }
    }
    
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleText by remember { mutableStateOf(uiState.note?.title ?: "Note") }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(uiState.note?.title) {
        if (uiState.note?.title != null) {
            titleText = uiState.note!!.title
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.note?.title ?: "Note",
                        modifier = Modifier.clickable { isEditingTitle = true }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showDeleteNoteDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.isRecording) {
                FloatingActionButton(
                    onClick = { viewModel.stopRecording() },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Stop Recording"
                    )
                }
            } else if (!uiState.quotaExceeded) {
                FloatingActionButton(
                    onClick = { viewModel.showAddEntryDialog() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Entry"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    val summary = uiState.summary
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        val maxHeight = maxHeight
                        val summaryMaxHeight = maxHeight * 0.5f // 50% of available height
                        
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Summary Section (Read-only at top, max 50% height, scrollable)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = summaryMaxHeight),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
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
                                        Text(
                                            text = "Oppsummering",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (!uiState.quotaExceeded && uiState.entries.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.generateSummaryManually() },
                                                enabled = !uiState.isGeneratingSummary
                                            ) {
                                                if (uiState.isGeneratingSummary) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Generer oppsummering"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (summary != null) {
                                        val scrollState = rememberScrollState()
                                        Text(
                                            text = summary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(scrollState)
                                        )
                                    } else if (uiState.isGeneratingSummary) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Genererer oppsummering...")
                                        }
                                    } else {
                                        Text(
                                            text = "Ingen oppsummering ennå. Legg til innlegg for å generere en oppsummering.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        
                            // Entries List Header
                            if (uiState.entries.isNotEmpty()) {
                                Text(
                                    text = "Entries",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                
                                // Scrollable entries list
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.entries) { entry ->
                                        NoteEntryItem(
                                            entry = entry,
                                            recording = uiState.recordings.find { it.id == entry.recordingId },
                                            onDelete = { entryToDelete = entry.id },
                                            onImageClick = if (entry.entryType == EntryType.IMAGE) {
                                                { imageToShow = entry.content }
                                            } else null
                                        )
                                    }
                                }
                            }
                            
                            // Loading indicators
                            if (uiState.isTranscribing) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Transcribing audio...")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
            
            // Quota exceeded banner
            if (uiState.quotaExceeded) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Monthly OpenAI limit reached",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Add Entry Dialog
            if (uiState.showAddEntryDialog) {
                AddEntryDialog(
                    onDismiss = { viewModel.hideAddEntryDialog() },
                    onAddText = { text ->
                        viewModel.addTextEntry(text)
                    },
                    onStartRecording = {
                        viewModel.hideAddEntryDialog()
                        if (audioPermissionState.allPermissionsGranted) {
                            viewModel.startRecording()
                        } else {
                            audioPermissionState.launchMultiplePermissionRequest()
                        }
                    },
                    onTakePhoto = {
                        viewModel.hideAddEntryDialog()
                        if (cameraPermissionState.allPermissionsGranted) {
                            // Create temporary file for camera using FileProvider
                            val imageDir = ImageHelper.getImageStorageDirectory(context)
                            val tempFile = File(imageDir, "temp_${System.currentTimeMillis()}.jpg")
                            tempImageFile = tempFile
                            val imageUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                            tempImageUri = imageUri
                            cameraLauncher.launch(imageUri)
                        } else {
                            cameraPermissionState.launchMultiplePermissionRequest()
                        }
                    }
                )
            }
            
            // Full-size image dialog
            imageToShow?.let { imagePath ->
                FullSizeImageDialog(
                    imageFilePath = imagePath,
                    onDismiss = { imageToShow = null }
                )
            }
            
            // Edit Title Dialog
            if (isEditingTitle) {
                AlertDialog(
                    onDismissRequest = { isEditingTitle = false },
                    title = { Text("Edit Note Title") },
                    text = {
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (titleText.isNotBlank()) {
                                    viewModel.updateNoteTitle(noteId, titleText)
                                }
                                isEditingTitle = false
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isEditingTitle = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Delete Note Confirmation Dialog
            if (showDeleteNoteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteNoteDialog = false },
                    title = { Text("Delete Note") },
                    text = { Text("Are you sure you want to delete this note? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteNote(noteId)
                                showDeleteNoteDialog = false
                                onBack()
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteNoteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Delete Entry Confirmation Dialog
            entryToDelete?.let { entryId ->
                AlertDialog(
                    onDismissRequest = { entryToDelete = null },
                    title = { Text("Delete Entry") },
                    text = { Text("Are you sure you want to delete this entry? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteEntry(entryId)
                                entryToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { entryToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AddEntryDialog(
    onDismiss: () -> Unit,
    onAddText: (String) -> Unit,
    onStartRecording: () -> Unit,
    onTakePhoto: () -> Unit
) {
    var showTextInput by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    
    if (showTextInput) {
        AlertDialog(
            onDismissRequest = {
                showTextInput = false
                text = ""
            },
            title = { Text("Add Text Entry") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Entry") },
                    placeholder = { Text("Type your note...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onAddText(text)
                            text = ""
                            showTextInput = false
                            onDismiss()
                        }
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTextInput = false
                    text = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add Entry") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            showTextInput = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Text")
                    }
                    
                    Button(
                        onClick = {
                            onStartRecording()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio")
                    }
                    
                    Button(
                        onClick = {
                            onTakePhoto()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Image")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
