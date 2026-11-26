package com.example.caprichometro.infrastructure.ui.caprice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.caprice.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.caprice.UploadCapriceImageUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.DeviceLocationException
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.infrastructure.location.UserLocationCellSyncHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class CreateCapriceState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLocationPermissionGranted: Boolean = false,
    val isNearbyDiscoveryEnabled: Boolean = false,
    val isPreparingNearbyLocation: Boolean = false,
    val preparedNearbyCell: String? = null,
    val preparedLocationCell: String? = null,
    val nearbyCreationError: String? = null,
    val nearbyCreationNeedsLocationServices: Boolean = false
)

class CreateCapriceViewModel(
    private val saveCapriceUseCase: SaveCapriceUseCase,
    private val uploadCapriceImageUseCase: UploadCapriceImageUseCase? = null,
    private val locationRepository: DeviceLocationRepository? = null,
    private val currentUserIdProvider: () -> String = { FirebaseAuth.getInstance().currentUser?.uid ?: "" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val userProfileRepository: UserProfileRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCapriceState())
    val state: StateFlow<CreateCapriceState> = _state.asStateFlow()

    private var nearbyCreationJob: Job? = null
    private var privateLocationJob: Job? = null
    private val syncedLocationCells = ConcurrentHashMap<String, String>()

    private val currentUserId: String
        get() = currentUserIdProvider()

    fun reset() {
        nearbyCreationJob?.cancel()
        nearbyCreationJob = null
        privateLocationJob?.cancel()
        privateLocationJob = null
        syncedLocationCells.clear()
        _state.value = CreateCapriceState()
    }

    fun selectType(
        previousIsCompra: Boolean,
        nextIsCompra: Boolean,
        currentAnswer1: String,
        currentAnswer2: String,
        compraDefaults: Pair<String, String>,
        planDefaults: Pair<String, String>
    ): Pair<String, String> {
        if (previousIsCompra == nextIsCompra) return currentAnswer1 to currentAnswer2
        val prevDefaults = if (previousIsCompra) compraDefaults else planDefaults
        val nextDefaults = if (nextIsCompra) compraDefaults else planDefaults
        return if (currentAnswer1 == prevDefaults.first && currentAnswer2 == prevDefaults.second) {
            nextDefaults
        } else {
            currentAnswer1 to currentAnswer2
        }
    }

    suspend fun saveCaprice(
        type: String,
        questionText: String,
        answer1: String,
        answer2: String,
        imageBytes: ByteArray? = null,
        visibleToUserIds: List<String>? = null,
        nearbyCell: String? = null,
        locationCell: String? = null,
        price: Double? = null
    ): Result<Caprice> {
        val uid = currentUserId
        if (uid.isBlank()) return Result.failure(IllegalStateException("No hay sesión activa"))

        val capriceBuilder = Caprice.Builder()
            .setUserId(uid)
            .setType(type)
            .setQuestion(questionText)
            .setOptions(answer1, answer2)
            .setPrice(price)

        if (visibleToUserIds != null) {
            capriceBuilder.setVisibleToUserIds(listOf(uid) + visibleToUserIds)
        }
        val normalizedCell = nearbyCell?.trim()?.takeIf { it.isNotEmpty() }
        var normalizedLocationCell = locationCell?.trim()?.takeIf { it.isNotEmpty() }
            ?: normalizedCell
        val hasPermNow = _state.value.isLocationPermissionGranted ||
            (locationRepository?.hasLocationPermission() == true)
        if (normalizedLocationCell == null && hasPermNow && locationRepository != null) {
            val resolvedLoc = withTimeoutOrNull(1_500L) {
                locationRepository.currentApproximateLocation().getOrNull()
            }
            if (resolvedLoc != null) {
                normalizedLocationCell = NearbyCellGrid.cellId(resolvedLoc)
            }
        }
        normalizedLocationCell?.let { syncUserLocationCellInBackground(uid, it) }
        capriceBuilder.setNearbyCell(normalizedCell)
        capriceBuilder.setLocationCell(normalizedLocationCell)

        val preliminaryCaprice = capriceBuilder.build()
        _state.update { it.copy(isLoading = true, error = null) }

        return try {
            var finalImageUrl = ""
            if (imageBytes != null && uploadCapriceImageUseCase != null) {
                val uploadResult = uploadCapriceImageUseCase.execute(imageBytes, preliminaryCaprice.id)
                val uploadError = uploadResult.exceptionOrNull()
                if (uploadError != null) {
                    runCatching { Log.e("CreateCapriceViewModel", "Error procesando imagen", uploadError) }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = uploadError.message ?: "No se pudo subir la imagen del capricho"
                        )
                    }
                    return Result.failure(uploadError)
                }
                finalImageUrl = uploadResult.getOrThrow()
            }

            val capriceToSave = capriceBuilder.setImageUrl(finalImageUrl).build()
            val result = saveCapriceUseCase.execute(capriceToSave)
            result.fold(
                onSuccess = { docId ->
                    val savedCaprice = capriceToSave.copy(id = docId)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isNearbyDiscoveryEnabled = false,
                            isPreparingNearbyLocation = false,
                            preparedNearbyCell = null,
                            preparedLocationCell = null,
                            nearbyCreationError = null
                        )
                    }
                    Result.success(savedCaprice)
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    Result.failure(e)
                }
            )
        } finally {
            _state.update { current ->
                if (current.isLoading) current.copy(isLoading = false) else current
            }
        }
    }

    fun onLocationPermissionChanged(granted: Boolean) {
        _state.update { it.copy(isLocationPermissionGranted = granted) }
        if (granted) {
            preparePrivateLocationCellIfPermitted()
        }
    }

    fun preparePrivateLocationCellIfPermitted() {
        val locRepo = locationRepository ?: return
        if (!locRepo.hasLocationPermission()) return
        if (!_state.value.isLocationPermissionGranted) {
            _state.update { it.copy(isLocationPermissionGranted = true) }
        }
        val uid = currentUserId
        val existingPrepared = _state.value.preparedLocationCell?.takeIf { it.isNotBlank() }
        if (existingPrepared != null) {
            syncUserLocationCellInBackground(uid, existingPrepared)
            return
        }
        if (privateLocationJob?.isActive == true) return

        privateLocationJob = viewModelScope.launch(ioDispatcher) {
            locRepo.currentApproximateLocation().onSuccess { location ->
                val cellId = NearbyCellGrid.cellId(location)
                _state.update { it.copy(preparedLocationCell = cellId) }
                syncUserLocationCellInBackground(uid, cellId)
            }
        }
    }

    private fun syncUserLocationCellInBackground(uid: String, cellId: String) {
        viewModelScope.launch(ioDispatcher) {
            UserLocationCellSyncHelper.syncIfChanged(
                userProfileReadRepository = userProfileRepository,
                userProfileWriteRepository = userProfileRepository,
                syncedLocationCells = syncedLocationCells,
                uid = uid,
                cellId = cellId
            )
        }
    }

    fun setNearbyDiscoveryEnabled(enabled: Boolean) {
        nearbyCreationJob?.cancel()
        nearbyCreationJob = null
        if (!enabled) {
            _state.update {
                it.copy(
                    isNearbyDiscoveryEnabled = false,
                    isPreparingNearbyLocation = false,
                    preparedNearbyCell = null,
                    nearbyCreationError = null,
                    nearbyCreationNeedsLocationServices = false
                )
            }
            return
        }

        val locRepo = locationRepository
        if (locRepo == null) {
            _state.update {
                it.copy(
                    isNearbyDiscoveryEnabled = false,
                    isPreparingNearbyLocation = false,
                    preparedNearbyCell = null,
                    nearbyCreationError = "Ubicación no disponible en este dispositivo.",
                    nearbyCreationNeedsLocationServices = false
                )
            }
            return
        }

        _state.update {
            it.copy(
                isNearbyDiscoveryEnabled = true,
                isPreparingNearbyLocation = true,
                preparedNearbyCell = null,
                nearbyCreationError = null,
                nearbyCreationNeedsLocationServices = false
            )
        }

        nearbyCreationJob = viewModelScope.launch {
            val result = locRepo.currentApproximateLocation()
            result.fold(
                onSuccess = { location ->
                    val cellId = NearbyCellGrid.cellId(location)
                    _state.update {
                        it.copy(
                            isLocationPermissionGranted = true,
                            isNearbyDiscoveryEnabled = true,
                            isPreparingNearbyLocation = false,
                            preparedNearbyCell = cellId,
                            preparedLocationCell = cellId,
                            nearbyCreationError = null
                        )
                    }
                    syncUserLocationCellInBackground(currentUserId, cellId)
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    _state.update {
                        it.copy(
                            isNearbyDiscoveryEnabled = false,
                            isPreparingNearbyLocation = false,
                            preparedNearbyCell = null,
                            nearbyCreationError = e.message ?: "No se pudo obtener tu ubicación aproximada.",
                            nearbyCreationNeedsLocationServices = e is DeviceLocationException.LocationServicesDisabled
                        )
                    }
                }
            )
        }
    }

    fun onNearbyCreationPermissionDenied() {
        nearbyCreationJob?.cancel()
        nearbyCreationJob = null
        _state.update {
            it.copy(
                isNearbyDiscoveryEnabled = false,
                isPreparingNearbyLocation = false,
                preparedNearbyCell = null,
                nearbyCreationError = "Necesitas permitir la ubicación aproximada para publicar también en General.",
                nearbyCreationNeedsLocationServices = false
            )
        }
    }

    fun clearNearbyCreationError() {
        _state.update { it.copy(nearbyCreationError = null, nearbyCreationNeedsLocationServices = false) }
    }

    fun resetNearbyCreationState() {
        nearbyCreationJob?.cancel()
        nearbyCreationJob = null
        _state.update {
            it.copy(
                isNearbyDiscoveryEnabled = false,
                isPreparingNearbyLocation = false,
                preparedNearbyCell = null,
                nearbyCreationError = null,
                nearbyCreationNeedsLocationServices = false
            )
        }
    }
}
