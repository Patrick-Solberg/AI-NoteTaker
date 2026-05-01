package com.example.ai_notetaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.example.ai_notetaker.ui.screen.NoteDetailScreen
import com.example.ai_notetaker.ui.screen.NotesListScreen
import com.example.ai_notetaker.ui.screen.TrashScreen
import com.example.ai_notetaker.ui.theme.AINoteTakerTheme
import com.example.ai_notetaker.ui.viewmodel.NoteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AINoteTakerTheme {
                NoteTakerApp()
            }
        }
    }
}

@Composable
fun NoteTakerApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel: NoteViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = "notes_list",
        modifier = modifier
    ) {
        composable(
            route = "notes_list",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            NotesListScreen(
                viewModel = viewModel,
                onNoteClick = { noteId ->
                    navController.navigate("note_detail/$noteId")
                },
                onNavigateToTrash = {
                    navController.navigate("trash")
                }
            )
        }
        
        composable(
            route = "note_detail/{noteId}",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull()
            if (noteId != null) {
                NoteDetailScreen(
                    noteId = noteId,
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            } else {
                // Handle invalid note ID
                NotesListScreen(
                    viewModel = viewModel,
                    onNoteClick = { id ->
                        navController.navigate("note_detail/$id")
                    }
                )
            }
        }
        
        composable(
            route = "trash",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            TrashScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}