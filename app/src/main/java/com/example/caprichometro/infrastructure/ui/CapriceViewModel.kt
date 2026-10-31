package com.example.caprichometro.infrastructure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.domain.model.Caprice
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CapriceState(
    val isLoading: Boolean = false,
    val latestCaprices: List<Caprice> = emptyList(),
    val error: String? = null
)

class CapriceViewModel(
    private val saveCapriceUseCase: SaveCapriceUseCase,
    private val getCapricesUseCase: GetCapricesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CapriceState())
    val state: StateFlow<CapriceState> = _state.asStateFlow()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    fun saveCaprice(type: String, questionText: String, answer1: String, answer2: String) {
        val uid = currentUserId
        if (uid.isBlank()) return

        val caprice = Caprice.Builder()
            .setUserId(uid)
            .setType(type)
            .setQuestion(questionText)
            .setOptions(answer1, answer2)
            .build()

        _state.update { it.copy(isLoading = true, latestCaprices = listOf(caprice), error = null) }

        viewModelScope.launch {
            val result = saveCapriceUseCase.execute(caprice)
            result.fold(
                onSuccess = {
                    fetchUserCaprices(limit = 1)
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun fetchUserCaprices(limit: Int = 1) {
        val uid = currentUserId
        if (uid.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val result = getCapricesUseCase.execute(uid, limit)

            result.fold(
                onSuccess = { capriceList ->
                    _state.update { it.copy(isLoading = false, latestCaprices = capriceList) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }
}
