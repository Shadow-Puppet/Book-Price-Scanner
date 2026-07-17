package com.example.bookscanprice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Scanning : UiState
    data object Loading : UiState
    data class Result(val book: BookInfo) : UiState
}

class BookViewModel : ViewModel() {

    private val repo = BookRepository()

    private val _state = MutableStateFlow<UiState>(UiState.Scanning)
    val state: StateFlow<UiState> = _state

    private var lastIsbn: String? = null

    fun onBarcode(raw: String) {
        // Book barcodes are EAN-13 starting 978/979, or ISBN-10.
        val isbn = raw.filter { it.isDigit() || it == 'X' }
        val valid = (isbn.length == 13 && (isbn.startsWith("978") || isbn.startsWith("979"))) ||
                isbn.length == 10
        if (!valid) return
        if (_state.value !is UiState.Scanning) return
        if (isbn == lastIsbn) return
        lastIsbn = isbn

        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = UiState.Result(repo.lookup(isbn))
        }
    }

    fun scanAgain() {
        lastIsbn = null
        _state.value = UiState.Scanning
    }
}
