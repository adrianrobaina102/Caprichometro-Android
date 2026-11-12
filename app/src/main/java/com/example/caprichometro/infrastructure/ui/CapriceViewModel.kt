package com.example.caprichometro.infrastructure.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.GetFriendsCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.UploadCapriceImageUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CapriceState(
    val isLoading: Boolean = false,
    val latestCaprices: List<Caprice> = emptyList(),
    val error: String? = null,
    val feedItems: List<CapriceFeedItem> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedError: String? = null,
    val hasLoadedFeed: Boolean = false
)

class CapriceViewModel(
    private val saveCapriceUseCase: SaveCapriceUseCase,
    private val getCapricesUseCase: GetCapricesUseCase,
    private val uploadCapriceImageUseCase: UploadCapriceImageUseCase? = null,
    private val getFriendsCapricesUseCase: GetFriendsCapricesUseCase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(CapriceState())
    val state: StateFlow<CapriceState> = _state.asStateFlow()

    private var feedLoadJob: Job? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    fun reset() {
        feedLoadJob?.cancel()
        feedLoadJob = null
        _state.value = CapriceState()
    }

    suspend fun saveCaprice(
        type: String,
        questionText: String,
        answer1: String,
        answer2: String,
        imageBytes: ByteArray? = null,
        visibleToUserIds: List<String>? = null
    ): Result<Caprice> {
        val uid = currentUserId
        if (uid.isBlank()) return Result.failure(IllegalStateException("No hay sesión activa"))

        val capriceBuilder = Caprice.Builder()
            .setUserId(uid)
            .setType(type)
            .setQuestion(questionText)
            .setOptions(answer1, answer2)

        if (visibleToUserIds != null) {
            capriceBuilder.setVisibleToUserIds(listOf(uid) + visibleToUserIds)
        }

        val preliminaryCaprice = capriceBuilder.build()
        _state.update { it.copy(isLoading = true, error = null) }

        var finalImageUrl = ""
        if (imageBytes != null && uploadCapriceImageUseCase != null) {
            val uploadResult = uploadCapriceImageUseCase.execute(imageBytes, preliminaryCaprice.id)
            uploadResult.fold(
                onSuccess = { url ->
                    finalImageUrl = url
                },
                onFailure = { e ->
                    Log.e("CapriceViewModel", "Error procesando imagen", e)
                }
            )
        }

        val capriceToSave = capriceBuilder.setImageUrl(finalImageUrl).build()
        val result = saveCapriceUseCase.execute(capriceToSave)
        return result.fold(
            onSuccess = { docId ->
                val savedCaprice = capriceToSave.copy(id = docId)
                _state.update { it.copy(isLoading = false, latestCaprices = listOf(savedCaprice)) }
                Result.success(savedCaprice)
            },
            onFailure = { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
                Result.failure(e)
            }
        )
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

    fun loadFriendsFeedIfNeeded() {
        if (!_state.value.hasLoadedFeed && !_state.value.isFeedLoading) {
            loadFriendsFeed()
        }
    }

    fun loadFriendsFeed() {
        val uid = currentUserId
        val useCase = getFriendsCapricesUseCase ?: return
        if (uid.isBlank()) return

        feedLoadJob?.cancel()
        feedLoadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isFeedLoading = true, feedError = null) }

            val result = useCase.execute(uid, perFriendLimit = 20, totalLimit = 20)
            result.fold(
                onSuccess = { items ->
                    Log.d("CapriceViewModel", "loadFriendsFeed(uid=$uid) -> ${items.size} items en el feed")
                    _state.update {
                        it.copy(
                            isFeedLoading = false,
                            feedItems = items,
                            feedError = null,
                            hasLoadedFeed = true
                        )
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    Log.e("CapriceViewModel", "Error en loadFriendsFeed: ${e.message}", e)
                    _state.update {
                        it.copy(
                            isFeedLoading = false,
                            feedError = e.message ?: "No se pudo cargar el feed de amigos."
                        )
                    }
                }
            )
        }
    }
}
