package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AuthRepository
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.ui.NotesScreen
import com.example.ui.NotesViewModel
import com.example.ui.NotesViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val database = AppDatabase.getDatabase(this)
    val repository = NoteRepository(database.noteDao())
    val authRepository = AuthRepository(this)
    val factory = NotesViewModelFactory(repository, authRepository)

    setContent {
      MyApplicationTheme {
        val viewModel: NotesViewModel = viewModel(factory = factory)
        NotesScreen(viewModel = viewModel)
      }
    }
  }
}


