package com.example.caprichometro.infrastructure.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.GetFriendsCapricesUseCase
import com.example.caprichometro.application.usecase.GetNearbyCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.UploadCapriceImageUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceCacheControl
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.DeviceLocationException
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeFeedTab {
    FRIENDS,
    GENERAL
}

data class CapriceState(
    val isLoading: Boolean = false,
    val latestCaprices: List<Caprice> = emptyList(),
    val error: String? = null,
    val feedItems: List<CapriceFeedItem> = emptyList(),
    val totalFriendsFeedCount: Int = 0,
    val hasMoreFriendsFeed: Boolean = false,
    val isFeedLoading: Boolean = false,
    val isFriendsBackgroundSyncing: Boolean = false,
    val feedError: String? = null,
    val hasLoadedFeed: Boolean = false,
    val selectedHomeTab: HomeFeedTab = HomeFeedTab.FRIENDS,
    val nearbyFeedItems: List<CapriceFeedItem> = emptyList(),
    val totalNearbyFeedCount: Int = 0,
    val hasMoreNearbyFeed: Boolean = false,
    val isNearbyFeedLoading: Boolean = false,
    val isNearbyBackgroundSyncing: Boolean = false,
    val nearbyFeedError: String? = null,
    val nearbyNeedsLocationPermission: Boolean = false,
    val nearbyNeedsLocationServices: Boolean = false,
    val hasLoadedNearbyFeed: Boolean = false,
    val isNearbyDiscoveryEnabled: Boolean = false,
    val isPreparingNearbyLocation: Boolean = false,
    val preparedNearbyCell: String? = null,
    val preparedLocationCell: String? = null,
    val nearbyCreationError: String? = null
)

class CapriceViewModel(
    private val saveCapriceUseCase: SaveCapriceUseCase,
    private val getCapricesUseCase: GetCapricesUseCase,
    private val uploadCapriceImageUseCase: UploadCapriceImageUseCase? = null,
    private val getFriendsCapricesUseCase: GetFriendsCapricesUseCase? = null,
    private val profileCacheControl: ProfileCacheControl? = null,
    private val getNearbyCapricesUseCase: GetNearbyCapricesUseCase? = null,
    private val locationRepository: DeviceLocationRepository? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val currentUserIdProvider: () -> String = { FirebaseAuth.getInstance().currentUser?.uid ?: "" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val capriceCacheControl: CapriceCacheControl? = null
) : ViewModel() {

    companion object {
        internal const val NEARBY_CACHE_TTL_MS = 60_000L
        internal const val INITIAL_FAST_BATCH_SIZE = 5
        internal const val FEED_PAGE_SIZE = 20
        internal const val FRIENDS_FETCH_LIMIT = 200
    }

    private val _state = MutableStateFlow(CapriceState())
    val state: StateFlow<CapriceState> = _state.asStateFlow()

    private var feedLoadJob: Job? = null
    private var nearbyFeedLoadJob: Job? = null
    private var nearbyCreationJob: Job? = null
    private var privateLocationJob: Job? = null
    private var lastNearbyLoadedAtMillis: Long? = null
    private var lastNearbyLoadedUserId: String? = null
    private var allFriendsFeedItems: List<CapriceFeedItem> = emptyList()
    private var friendsVisibleCount: Int = FEED_PAGE_SIZE
    private var allNearbyFeedItems: List<CapriceFeedItem> = emptyList()
    private var nearbyVisibleCount: Int = FEED_PAGE_SIZE

    private val currentUserId: String
        get() = currentUserIdProvider()

    fun reset() {
        feedLoadJob?.cancel()
        feedLoadJob = null
        nearbyFeedLoadJob?.cancel()
        nearbyFeedLoadJob = null
        nearbyCreationJob?.cancel()
        nearbyCreationJob = null
        privateLocationJob?.cancel()
        privateLocationJob = null
        lastNearbyLoadedAtMillis = null
        lastNearbyLoadedUserId = null
        allFriendsFeedItems = emptyList()
        friendsVisibleCount = FEED_PAGE_SIZE
        allNearbyFeedItems = emptyList()
        nearbyVisibleCount = FEED_PAGE_SIZE
        _state.value = CapriceState()
    }

    suspend fun saveCaprice(
        type: String,
        questionText: String,
        answer1: String,
        answer2: String,
        imageBytes: ByteArray? = null,
        visibleToUserIds: List<String>? = null,
        nearbyCell: String? = null,
        locationCell: String? = null
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
        val normalizedCell = nearbyCell?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedLocationCell = locationCell?.trim()?.takeIf { it.isNotEmpty() }
        capriceBuilder.setNearbyCell(normalizedCell)
        capriceBuilder.setLocationCell(normalizedLocationCell)

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
                _state.update {
                    it.copy(
                        isLoading = false,
                        latestCaprices = listOf(savedCaprice),
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
        val current = _state.value
        if (!current.hasLoadedFeed && !current.isFeedLoading && !current.isFriendsBackgroundSyncing) {
            loadFriendsFeed(isManualRefresh = false)
        }
    }

    fun loadFriendsFeed(isManualRefresh: Boolean = false) {
        val uid = currentUserId
        val useCase = getFriendsCapricesUseCase ?: return
        if (uid.isBlank()) return

        feedLoadJob?.cancel()
        // Marcamos la carga síncronamente para evitar carreras al arrancar entre MainActivity y HomeScreen
        _state.update {
            it.copy(
                isFeedLoading = true,
                isFriendsBackgroundSyncing = false,
                feedError = null
            )
        }
        feedLoadJob = viewModelScope.launch(ioDispatcher) {
            val startedAt = clockMillis()
            if (isManualRefresh) {
                friendsVisibleCount = FEED_PAGE_SIZE
                profileCacheControl?.invalidateAll()
                capriceCacheControl?.invalidateAll()
            }

            var loggedFirstServerBatch = false
            useCase.observeProgressiveFeed(
                currentUserId = uid,
                initialBatchSize = INITIAL_FAST_BATCH_SIZE,
                perFriendLimit = 100,
                totalLimit = FRIENDS_FETCH_LIMIT,
                includeCache = !isManualRefresh
            ).catch { e ->
                if (e is CancellationException) throw e
                if (currentUserId != uid) return@catch
                Log.e("CapriceViewModel", "Error en loadFriendsFeed: ${e.message}", e)
                _state.update {
                    it.copy(
                        isFeedLoading = false,
                        isFriendsBackgroundSyncing = false,
                        feedError = e.message ?: "No se pudo cargar el feed de amigos."
                    )
                }
            }.collect { batch ->
                if (currentUserId != uid) return@collect
                allFriendsFeedItems = batch.items
                val visible = batch.items.take(friendsVisibleCount.coerceAtLeast(FEED_PAGE_SIZE))
                val elapsedMs = clockMillis() - startedAt
                when {
                    batch.isFromCache -> logPerf(
                        "loadFriendsFeed[cache](uid=$uid) -> total=${batch.items.size}, visible=${visible.size} en $elapsedMs ms"
                    )
                    !batch.isComplete && !loggedFirstServerBatch -> {
                        loggedFirstServerBatch = true
                        logPerf(
                            "loadFriendsFeed[firstBatch](uid=$uid, manual=$isManualRefresh) -> total=${batch.items.size}, visible=${visible.size} en $elapsedMs ms"
                        )
                    }
                    batch.isComplete -> logPerf(
                        "loadFriendsFeed[complete](uid=$uid, manual=$isManualRefresh) -> total=${batch.items.size}, visible=${visible.size} en $elapsedMs ms"
                    )
                }
                _state.update {
                    it.copy(
                        isFeedLoading = false,
                        isFriendsBackgroundSyncing = !batch.isComplete,
                        feedItems = visible,
                        totalFriendsFeedCount = batch.items.size,
                        hasMoreFriendsFeed = batch.items.size > visible.size,
                        feedError = null,
                        hasLoadedFeed = true
                    )
                }
            }
        }
    }

    fun loadMoreFriendsFeed() {
        if (allFriendsFeedItems.isEmpty()) return
        val nextCount = (friendsVisibleCount + FEED_PAGE_SIZE).coerceAtMost(allFriendsFeedItems.size)
        if (nextCount <= friendsVisibleCount) return
        friendsVisibleCount = nextCount
        val visible = allFriendsFeedItems.take(friendsVisibleCount)
        _state.update {
            it.copy(
                feedItems = visible,
                totalFriendsFeedCount = allFriendsFeedItems.size,
                hasMoreFriendsFeed = allFriendsFeedItems.size > visible.size
            )
        }
    }

    fun selectHomeTab(tab: HomeFeedTab) {
        _state.update { it.copy(selectedHomeTab = tab) }
    }

    fun loadNearbyFeedIfNeeded() {
        val uid = currentUserId
        if (uid.isBlank() || _state.value.isNearbyFeedLoading || _state.value.isNearbyBackgroundSyncing) return

        val sameUser = lastNearbyLoadedUserId == uid
        val loadedAt = lastNearbyLoadedAtMillis
        val isFreshCache = sameUser &&
            loadedAt != null &&
            _state.value.hasLoadedNearbyFeed &&
            _state.value.nearbyFeedError == null &&
            !_state.value.nearbyNeedsLocationPermission &&
            !_state.value.nearbyNeedsLocationServices &&
            (clockMillis() - loadedAt) < NEARBY_CACHE_TTL_MS

        if (isFreshCache) return

        if (locationRepository != null && !locationRepository.hasLocationPermission()) {
            if (!sameUser) {
                allNearbyFeedItems = emptyList()
                nearbyVisibleCount = FEED_PAGE_SIZE
            }
            _state.update {
                it.copy(
                    nearbyFeedItems = if (sameUser) it.nearbyFeedItems else emptyList(),
                    totalNearbyFeedCount = if (sameUser) it.totalNearbyFeedCount else 0,
                    hasMoreNearbyFeed = if (sameUser) it.hasMoreNearbyFeed else false,
                    hasLoadedNearbyFeed = if (sameUser) it.hasLoadedNearbyFeed else false,
                    isNearbyBackgroundSyncing = false,
                    nearbyNeedsLocationPermission = true,
                    nearbyNeedsLocationServices = false,
                    nearbyFeedError = null
                )
            }
            return
        }

        loadNearbyFeed()
    }

    fun loadNearbyFeed(isManualRefresh: Boolean = false) {
        val uid = currentUserId
        val useCase = getNearbyCapricesUseCase ?: return
        if (uid.isBlank()) return

        if (lastNearbyLoadedUserId != null && lastNearbyLoadedUserId != uid) {
            lastNearbyLoadedAtMillis = null
            lastNearbyLoadedUserId = null
            allNearbyFeedItems = emptyList()
            nearbyVisibleCount = FEED_PAGE_SIZE
            _state.update {
                it.copy(
                    nearbyFeedItems = emptyList(),
                    totalNearbyFeedCount = 0,
                    hasMoreNearbyFeed = false,
                    hasLoadedNearbyFeed = false,
                    isNearbyBackgroundSyncing = false,
                    nearbyFeedError = null
                )
            }
        }

        if (locationRepository != null && !locationRepository.hasLocationPermission()) {
            _state.update {
                it.copy(
                    isNearbyFeedLoading = false,
                    isNearbyBackgroundSyncing = false,
                    nearbyNeedsLocationPermission = true,
                    nearbyNeedsLocationServices = false,
                    nearbyFeedError = null
                )
            }
            return
        }

        nearbyFeedLoadJob?.cancel()
        _state.update {
            it.copy(
                isNearbyFeedLoading = true,
                isNearbyBackgroundSyncing = false,
                nearbyFeedError = null,
                nearbyNeedsLocationPermission = false,
                nearbyNeedsLocationServices = false
            )
        }
        nearbyFeedLoadJob = viewModelScope.launch(ioDispatcher) {
            val startedAt = clockMillis()
            if (isManualRefresh) {
                nearbyVisibleCount = FEED_PAGE_SIZE
                profileCacheControl?.invalidateAll()
                capriceCacheControl?.invalidateAll()
            }

            var loggedFirstServerBatch = false
            useCase.observeProgressiveFeed(
                currentUserId = uid,
                includeCache = !isManualRefresh,
                initialBatchSize = INITIAL_FAST_BATCH_SIZE,
                pageSize = FEED_PAGE_SIZE
            ).catch { e ->
                if (e is CancellationException) throw e
                if (currentUserId != uid) return@catch
                _state.update { current ->
                    when (e) {
                        is DeviceLocationException.PermissionDenied -> current.copy(
                            isNearbyFeedLoading = false,
                            isNearbyBackgroundSyncing = false,
                            nearbyNeedsLocationPermission = true,
                            nearbyNeedsLocationServices = false,
                            nearbyFeedError = null
                        )
                        is DeviceLocationException.LocationServicesDisabled -> current.copy(
                            isNearbyFeedLoading = false,
                            isNearbyBackgroundSyncing = false,
                            nearbyNeedsLocationPermission = false,
                            nearbyNeedsLocationServices = true,
                            nearbyFeedError = e.message
                        )
                        else -> current.copy(
                            isNearbyFeedLoading = false,
                            isNearbyBackgroundSyncing = false,
                            nearbyNeedsLocationPermission = false,
                            nearbyNeedsLocationServices = false,
                            nearbyFeedError = e.message ?: "No se pudo cargar General."
                        )
                    }
                }
            }.collect { batch ->
                if (currentUserId != uid) return@collect
                if (batch.isComplete) {
                    lastNearbyLoadedUserId = uid
                    lastNearbyLoadedAtMillis = clockMillis()
                }
                allNearbyFeedItems = batch.items
                val visible = batch.items.take(nearbyVisibleCount.coerceAtLeast(FEED_PAGE_SIZE))
                val elapsedMs = clockMillis() - startedAt
                when {
                    batch.isFromCache -> logPerf(
                        "loadNearbyFeed[cache](uid=$uid) -> total=${batch.items.size}, visible=${visible.size} en $elapsedMs ms"
                    )
                    !batch.isComplete && !loggedFirstServerBatch -> {
                        loggedFirstServerBatch = true
                        logPerf(
                            "loadNearbyFeed[firstBatch](uid=$uid, manual=$isManualRefresh) -> total=${batch.items.size}, visible=${visible.size} en $elapsedMs ms"
                        )
                    }
                    batch.isComplete -> logPerf(
                        "loadNearbyFeed[complete](uid=$uid, manual=$isManualRefresh) -> total=${batch.items.size}, visible=${visible.size} en $elapsedMs ms"
                    )
                }
                _state.update {
                    it.copy(
                        isNearbyFeedLoading = false,
                        isNearbyBackgroundSyncing = !batch.isComplete,
                        nearbyFeedItems = visible,
                        totalNearbyFeedCount = batch.items.size,
                        hasMoreNearbyFeed = batch.items.size > visible.size,
                        nearbyFeedError = null,
                        nearbyNeedsLocationPermission = false,
                        nearbyNeedsLocationServices = false,
                        hasLoadedNearbyFeed = true
                    )
                }
            }
        }
    }

    fun loadMoreNearbyFeed() {
        if (allNearbyFeedItems.isEmpty()) return
        val nextCount = (nearbyVisibleCount + FEED_PAGE_SIZE).coerceAtMost(allNearbyFeedItems.size)
        if (nextCount <= nearbyVisibleCount) return
        nearbyVisibleCount = nextCount
        val visible = allNearbyFeedItems.take(nearbyVisibleCount)
        _state.update {
            it.copy(
                nearbyFeedItems = visible,
                totalNearbyFeedCount = allNearbyFeedItems.size,
                hasMoreNearbyFeed = allNearbyFeedItems.size > visible.size
            )
        }
    }

    fun onNearbyFeedPermissionDenied() {
        nearbyFeedLoadJob?.cancel()
        _state.update {
            it.copy(
                isNearbyFeedLoading = false,
                isNearbyBackgroundSyncing = false,
                nearbyNeedsLocationPermission = true,
                nearbyNeedsLocationServices = false,
                nearbyFeedError = null
            )
        }
    }

    fun preparePrivateLocationCellIfPermitted() {
        val locRepo = locationRepository ?: return
        if (!locRepo.hasLocationPermission()) return
        if (!_state.value.preparedLocationCell.isNullOrBlank()) return
        if (privateLocationJob?.isActive == true) return

        privateLocationJob = viewModelScope.launch(ioDispatcher) {
            locRepo.currentApproximateLocation().onSuccess { location ->
                val cellId = NearbyCellGrid.cellId(location)
                _state.update { it.copy(preparedLocationCell = cellId) }
            }
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
                    nearbyCreationError = null
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
                    nearbyCreationError = "Ubicación no disponible en este dispositivo."
                )
            }
            return
        }

        _state.update {
            it.copy(
                isNearbyDiscoveryEnabled = true,
                isPreparingNearbyLocation = true,
                preparedNearbyCell = null,
                nearbyCreationError = null
            )
        }

        nearbyCreationJob = viewModelScope.launch {
            val result = locRepo.currentApproximateLocation()
            result.fold(
                onSuccess = { location ->
                    val cellId = NearbyCellGrid.cellId(location)
                    _state.update {
                        it.copy(
                            isNearbyDiscoveryEnabled = true,
                            isPreparingNearbyLocation = false,
                            preparedNearbyCell = cellId,
                            preparedLocationCell = cellId,
                            nearbyCreationError = null
                        )
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    _state.update {
                        it.copy(
                            isNearbyDiscoveryEnabled = false,
                            isPreparingNearbyLocation = false,
                            preparedNearbyCell = null,
                            nearbyCreationError = e.message ?: "No se pudo obtener tu ubicación aproximada."
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
                nearbyCreationError = "Necesitas permitir la ubicación aproximada para publicar también en General."
            )
        }
    }

    fun clearNearbyCreationError() {
        _state.update { it.copy(nearbyCreationError = null) }
    }

    fun markFinalDecision(capriceId: String, decision: Int, decidedAt: Long) {
        _state.update { current ->
            current.copy(
                latestCaprices = current.latestCaprices.map { caprice ->
                    if (caprice.id == capriceId) {
                        caprice.copy(finalDecision = decision, finalDecisionAt = decidedAt)
                    } else caprice
                }
            )
        }
    }

    private fun logPerf(message: String) {
        runCatching { Log.d("FeedPerf", message) }
    }
}
