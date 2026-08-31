package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthRepository
import com.example.auth.UserProfile
import com.example.data.Note
import com.example.data.NoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorState(
    val isOpen: Boolean = false,
    val noteId: Long = 0,
    val title: String = "",
    val content: String = "",
    val category: String = "General",
    val colorIndex: Int = 0,
    val isPinned: Boolean = false,
    val isEditing: Boolean = false
)

class NotesViewModel(
    private val repository: NoteRepository,
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    val categories = listOf("All", "General", "Personal", "Work", "Ideas", "Quick Notes")

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")

    val currentUser: StateFlow<UserProfile?> = (authRepository?.userFlow
        ?: MutableStateFlow(null)).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = authRepository?.currentUser
    )

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading

    private val _authError = MutableSharedFlow<String>()
    val authError: SharedFlow<String> = _authError.asSharedFlow()

    private val _isProfileSheetOpen = MutableStateFlow(false)
    val isProfileSheetOpen: StateFlow<Boolean> = _isProfileSheetOpen

    val notes: StateFlow<List<Note>> = combine(
        repository.allNotes,
        searchQuery,
        selectedCategory
    ) { allNotes, query, category ->
        allNotes.filter { note ->
            val matchesQuery = query.isBlank() ||
                note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true)

            val matchesCategory = category == "All" || note.category.equals(category, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState

    private val _noteToDelete = MutableStateFlow<Note?>(null)
    val noteToDelete: StateFlow<Note?> = _noteToDelete

    fun openProfileSheet() {
        _isProfileSheetOpen.value = true
    }

    fun closeProfileSheet() {
        _isProfileSheetOpen.value = false
    }

    fun signInWithGoogle(webClientId: String? = null) {
        if (authRepository == null) return
        viewModelScope.launch {
            _isAuthLoading.value = true
            val result = authRepository.signInWithGoogle(webClientId)
            _isAuthLoading.value = false
            result.onFailure { error ->
                _authError.emit(error.message ?: "Google Sign-In failed")
            }
        }
    }

    fun signOut() {
        authRepository?.signOut()
        closeProfileSheet()
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectCategory(category: String) {
        selectedCategory.value = category
    }

    fun openNewNote() {
        val currentCategory = if (selectedCategory.value != "All") selectedCategory.value else "General"
        _editorState.value = EditorState(
            isOpen = true,
            noteId = 0,
            title = "",
            content = "",
            category = currentCategory,
            colorIndex = 0,
            isPinned = false,
            isEditing = false
        )
    }

    fun openEditNote(note: Note) {
        _editorState.value = EditorState(
            isOpen = true,
            noteId = note.id,
            title = note.title,
            content = note.content,
            category = note.category,
            colorIndex = note.colorIndex,
            isPinned = note.isPinned,
            isEditing = true
        )
    }

    fun updateTitle(title: String) {
        _editorState.value = _editorState.value.copy(title = title)
    }

    fun updateContent(content: String) {
        _editorState.value = _editorState.value.copy(content = content)
    }

    fun updateCategory(category: String) {
        _editorState.value = _editorState.value.copy(category = category)
    }

    fun updateColorIndex(colorIndex: Int) {
        _editorState.value = _editorState.value.copy(colorIndex = colorIndex)
    }

    fun toggleEditorPin() {
        _editorState.value = _editorState.value.copy(isPinned = !_editorState.value.isPinned)
    }

    fun closeEditor() {
        _editorState.value = EditorState(isOpen = false)
    }

    fun saveNote() {
        val state = _editorState.value
        if (state.title.isBlank() && state.content.isBlank()) {
            closeEditor()
            return
        }

        viewModelScope.launch {
            val titleToSave = if (state.title.isBlank()) "Untitled" else state.title.trim()
            val note = Note(
                id = state.noteId,
                title = titleToSave,
                content = state.content.trim(),
                timestamp = System.currentTimeMillis(),
                colorIndex = state.colorIndex,
                isPinned = state.isPinned,
                category = state.category.ifBlank { "General" }
            )

            if (state.isEditing && state.noteId > 0) {
                repository.update(note)
            } else {
                repository.insert(note)
            }
            closeEditor()
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.togglePin(note.id, note.isPinned)
        }
    }

    fun promptDelete(note: Note) {
        _noteToDelete.value = note
    }

    fun dismissDeletePrompt() {
        _noteToDelete.value = null
    }

    fun confirmDelete() {
        val note = _noteToDelete.value ?: return
        viewModelScope.launch {
            repository.delete(note)
            if (_editorState.value.isOpen && _editorState.value.noteId == note.id) {
                closeEditor()
            }
            _noteToDelete.value = null
        }
    }
}

class NotesViewModelFactory(
    private val repository: NoteRepository,
    private val authRepository: AuthRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(repository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

