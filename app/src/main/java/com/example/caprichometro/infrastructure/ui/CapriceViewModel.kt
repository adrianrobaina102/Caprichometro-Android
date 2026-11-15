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
import com.example.caprichometro.domain.model.ProgressiveFeedBatch
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

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
    val isLocationPermissionGranted: Boolean = false,
    val hasAutoPromptedLocation: Boolean = false,
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
    private val capriceCacheControl: CapriceCacheControl? = null,
    private val userProfileRepository: UserProfileRepository? = null
) : ViewModel() {

    companion object {
        internal const val NEARBY_CACHE_TTL_MS = 60_000L
        internal const val INITIAL_FAST_BATCH_SIZE = 5
        internal const val FEED_PAGE_SIZE = 20
        internal const val FRIENDS_FETCH_LIMIT = 200
        internal const val FEED_REVEAL_STEP_DELAY_MS = 60L
    }

    private class PagedFeedController {
        var loadedItems: List<CapriceFeedItem> = emptyList()
        var totalAvailableCount: Int = 0
        var visiblePageLimit: Int = FEED_PAGE_SIZE
        var revealedCount: Int = 0
        var isSourceComplete: Boolean = false
        var loadedWithLocationPermission: Boolean = false
        var revealJob: Job? = null
        var loadMoreJob: Job? = null

        fun reset() {
            revealJob?.cancel()
            revealJob = null
            loadMoreJob?.cancel()
            loadMoreJob = null
            loadedItems = emptyList()
            totalAvailableCount = 0
            visiblePageLimit = FEED_PAGE_SIZE
            revealedCount = 0
            isSourceComplete = false
            loadedWithLocationPermission = false
        }
    }

    private val _state = MutableStateFlow(CapriceState())
    val state: StateFlow<CapriceState> = _state.asStateFlow()

    private var feedLoadJob: Job? = null
    private var distanceRefreshJob: Job? = null
    private var nearbyFeedLoadJob: Job? = null
    private var nearbyCreationJob: Job? = null
    private var privateLocationJob: Job? = null
    private var lastNearbyLoadedAtMillis: Long? = null
    private var lastNearbyLoadedUserId: String? = null
    private val friendsController = PagedFeedController()
    private val nearbyController = PagedFeedController()
    private val syncedLocationCells = ConcurrentHashMap<String, String>()

    private val currentUserId: String
        get() = currentUserIdProvider()

    fun reset() {
        feedLoadJob?.cancel()
        feedLoadJob = null
        distanceRefreshJob?.cancel()
        distanceRefreshJob = null
        nearbyFeedLoadJob?.cancel()
        nearbyFeedLoadJob = null
        nearbyCreationJob?.cancel()
        nearbyCreationJob = null
        privateLocationJob?.cancel()
        privateLocationJob = null
        lastNearbyLoadedAtMillis = null
        lastNearbyLoadedUserId = null
        syncedLocationCells.clear()
        friendsController.reset()
        nearbyController.reset()
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
        distanceRefreshJob?.cancel()
        friendsController.revealJob?.cancel()
        friendsController.loadMoreJob?.cancel()
        if (isManualRefresh) {
            friendsController.reset()
        } else {
            friendsController.visiblePageLimit = FEED_PAGE_SIZE
            friendsController.isSourceComplete = false
        }
        val startedWithPermission = _state.value.isLocationPermissionGranted
        friendsController.loadedWithLocationPermission = startedWithPermission
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
                val totalKnown = maxOf(batch.totalAvailableCount, batch.items.size)
                val visibleTarget = minOf(friendsController.visiblePageLimit, batch.items.size)
                val elapsedMs = clockMillis() - startedAt
                when {
                    batch.isFromCache -> logPerf(
                        "loadFriendsFeed[cache](uid=$uid) -> total=$totalKnown, hydrated=${batch.items.size}, pageCap=$visibleTarget en $elapsedMs ms"
                    )
                    !batch.isComplete && !loggedFirstServerBatch -> {
                        loggedFirstServerBatch = true
                        logPerf(
                            "loadFriendsFeed[firstBatch](uid=$uid, manual=$isManualRefresh) -> total=$totalKnown, hydrated=${batch.items.size} en $elapsedMs ms"
                        )
                    }
                    batch.isComplete -> logPerf(
                        "loadFriendsFeed[complete](uid=$uid, manual=$isManualRefresh) -> total=$totalKnown, hydrated=${batch.items.size} en $elapsedMs ms"
                    )
                }
                val resolvedWithLocation = batch.hasViewerLocation ||
                    batch.items.any { it.approximateDistanceMeters != null }
                if (batch.isComplete) {
                    friendsController.loadedWithLocationPermission = resolvedWithLocation
                }
                applyProgressiveBatch(
                    controller = friendsController,
                    batch = batch,
                    uid = uid,
                    publishState = ::publishFriendsState
                )
                if (batch.isComplete && _state.value.isLocationPermissionGranted) {
                    val grantedDuringLoad = !startedWithPermission
                    if (userProfileRepository != null && (resolvedWithLocation || grantedDuringLoad)) {
                        preparePrivateLocationCellIfPermitted()
                    }
                    if (!resolvedWithLocation && grantedDuringLoad) {
                        refreshFriendsDistancesIfNeeded(onlyIfMissingDistanceOnLoadedItems = false)
                    }
                }
            }
        }
    }

    fun loadMoreFriendsFeed() {
        val useCase = getFriendsCapricesUseCase
        loadNextFeedPage(
            controller = friendsController,
            nextPageFlowProvider = useCase?.let { uc ->
                { loadedCount ->
                    uc.observeNextPage(
                        currentUserId = currentUserId,
                        currentLoadedCount = loadedCount,
                        initialBatchSize = INITIAL_FAST_BATCH_SIZE,
                        pageSize = FEED_PAGE_SIZE
                    )
                }
            },
            publishState = ::publishFriendsState
        )
    }

    private fun publishFriendsState(
        visibleItems: List<CapriceFeedItem>,
        totalCount: Int,
        hasMore: Boolean,
        isBackgroundSyncing: Boolean
    ) {
        _state.update {
            it.copy(
                isFeedLoading = false,
                isFriendsBackgroundSyncing = isBackgroundSyncing,
                feedItems = visibleItems,
                totalFriendsFeedCount = totalCount,
                hasMoreFriendsFeed = hasMore,
                feedError = null,
                hasLoadedFeed = true
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
                nearbyController.reset()
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
            nearbyController.reset()
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
        nearbyController.revealJob?.cancel()
        nearbyController.loadMoreJob?.cancel()
        if (isManualRefresh) {
            nearbyController.reset()
        } else {
            nearbyController.visiblePageLimit = FEED_PAGE_SIZE
            nearbyController.isSourceComplete = false
        }
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
                    _state.update { it.copy(isLocationPermissionGranted = true) }
                    refreshFriendsDistancesIfNeeded(onlyIfMissingDistanceOnLoadedItems = true)
                }
                val totalKnown = maxOf(batch.totalAvailableCount, batch.items.size)
                val visibleTarget = minOf(nearbyController.visiblePageLimit, batch.items.size)
                val elapsedMs = clockMillis() - startedAt
                when {
                    batch.isFromCache -> logPerf(
                        "loadNearbyFeed[cache](uid=$uid) -> total=$totalKnown, hydrated=${batch.items.size}, pageCap=$visibleTarget en $elapsedMs ms"
                    )
                    !batch.isComplete && !loggedFirstServerBatch -> {
                        loggedFirstServerBatch = true
                        logPerf(
                            "loadNearbyFeed[firstBatch](uid=$uid, manual=$isManualRefresh) -> total=$totalKnown, hydrated=${batch.items.size} en $elapsedMs ms"
                        )
                    }
                    batch.isComplete -> logPerf(
                        "loadNearbyFeed[complete](uid=$uid, manual=$isManualRefresh) -> total=$totalKnown, hydrated=${batch.items.size} en $elapsedMs ms"
                    )
                }
                applyProgressiveBatch(
                    controller = nearbyController,
                    batch = batch,
                    uid = uid,
                    publishState = ::publishNearbyState
                )
                if (batch.isComplete && userProfileRepository != null) {
                    preparePrivateLocationCellIfPermitted()
                }
            }
        }
    }

    fun loadMoreNearbyFeed() {
        val useCase = getNearbyCapricesUseCase
        loadNextFeedPage(
            controller = nearbyController,
            nextPageFlowProvider = useCase?.let { uc ->
                { loadedCount ->
                    uc.observeNextPage(
                        currentUserId = currentUserId,
                        currentLoadedCount = loadedCount,
                        initialBatchSize = INITIAL_FAST_BATCH_SIZE,
                        pageSize = FEED_PAGE_SIZE
                    )
                }
            },
            publishState = ::publishNearbyState
        )
    }

    private fun publishNearbyState(
        visibleItems: List<CapriceFeedItem>,
        totalCount: Int,
        hasMore: Boolean,
        isBackgroundSyncing: Boolean
    ) {
        _state.update {
            it.copy(
                isNearbyFeedLoading = false,
                isNearbyBackgroundSyncing = isBackgroundSyncing,
                nearbyFeedItems = visibleItems,
                totalNearbyFeedCount = totalCount,
                hasMoreNearbyFeed = hasMore,
                nearbyFeedError = null,
                nearbyNeedsLocationPermission = false,
                nearbyNeedsLocationServices = false,
                hasLoadedNearbyFeed = true
            )
        }
    }

    private fun applyProgressiveBatch(
        controller: PagedFeedController,
        batch: ProgressiveFeedBatch,
        uid: String,
        publishState: (List<CapriceFeedItem>, Int, Boolean, Boolean) -> Unit
    ) {
        controller.loadedItems = batch.items
        controller.totalAvailableCount = maxOf(batch.totalAvailableCount, batch.items.size)
        if (batch.isComplete) {
            controller.isSourceComplete = true
        }
        val targetInWindow = minOf(controller.visiblePageLimit, controller.loadedItems.size)
        val currentPageIndex = ((controller.visiblePageLimit - 1).coerceAtLeast(0)) / FEED_PAGE_SIZE
        val pageStart = currentPageIndex * FEED_PAGE_SIZE
        val instantEnd = minOf(pageStart + INITIAL_FAST_BATCH_SIZE, targetInWindow)
        controller.revealedCount = when {
            controller.revealedCount < instantEnd -> instantEnd
            controller.revealedCount > targetInWindow -> targetInWindow
            else -> controller.revealedCount
        }
        scheduleProgressiveReveal(controller, uid, publishState)
    }

    private fun loadNextFeedPage(
        controller: PagedFeedController,
        nextPageFlowProvider: ((Int) -> Flow<ProgressiveFeedBatch>)?,
        publishState: (List<CapriceFeedItem>, Int, Boolean, Boolean) -> Unit
    ) {
        val uid = currentUserId
        if (uid.isBlank()) return
        val knownTotal = maxOf(controller.totalAvailableCount, controller.loadedItems.size)
        if (knownTotal == 0) return

        val nextLimit = (controller.visiblePageLimit + FEED_PAGE_SIZE).coerceAtMost(knownTotal)
        if (nextLimit <= controller.visiblePageLimit &&
            controller.revealedCount >= minOf(controller.visiblePageLimit, controller.loadedItems.size)
        ) {
            return
        }
        controller.visiblePageLimit = nextLimit

        if (controller.loadedItems.size >= nextLimit || nextPageFlowProvider == null) {
            val targetInWindow = minOf(nextLimit, controller.loadedItems.size)
            controller.revealedCount = (controller.revealedCount + INITIAL_FAST_BATCH_SIZE)
                .coerceAtMost(targetInWindow)
            scheduleProgressiveReveal(controller, uid, publishState)
            return
        }

        if (controller.loadMoreJob?.isActive == true) return
        controller.isSourceComplete = false
        publishControllerSnapshot(controller, publishState)
        controller.loadMoreJob = viewModelScope.launch(ioDispatcher) {
            nextPageFlowProvider(controller.loadedItems.size)
                .catch { e ->
                    if (e is CancellationException) throw e
                    controller.isSourceComplete = true
                    publishControllerSnapshot(controller, publishState)
                }
                .collect { batch ->
                    if (currentUserId != uid) return@collect
                    applyProgressiveBatch(controller, batch, uid, publishState)
                }
        }
    }

    private fun scheduleProgressiveReveal(
        controller: PagedFeedController,
        uid: String,
        publishState: (List<CapriceFeedItem>, Int, Boolean, Boolean) -> Unit
    ) {
        controller.revealJob?.cancel()
        controller.revealJob = null
        publishControllerSnapshot(controller, publishState)

        val targetInWindow = minOf(controller.visiblePageLimit, controller.loadedItems.size)
        if (controller.revealedCount < targetInWindow) {
            controller.revealJob = viewModelScope.launch(ioDispatcher) {
                while (controller.revealedCount < minOf(controller.visiblePageLimit, controller.loadedItems.size)) {
                    delay(FEED_REVEAL_STEP_DELAY_MS)
                    if (currentUserId != uid) return@launch
                    val cap = minOf(controller.visiblePageLimit, controller.loadedItems.size)
                    controller.revealedCount = (controller.revealedCount + INITIAL_FAST_BATCH_SIZE).coerceAtMost(cap)
                    publishControllerSnapshot(controller, publishState)
                }
            }
        }
    }

    private fun publishControllerSnapshot(
        controller: PagedFeedController,
        publishState: (List<CapriceFeedItem>, Int, Boolean, Boolean) -> Unit
    ) {
        val cap = minOf(controller.visiblePageLimit, controller.loadedItems.size)
        val visibleCount = controller.revealedCount.coerceAtMost(cap)
        val total = maxOf(controller.totalAvailableCount, controller.loadedItems.size)
        val stillStepping = visibleCount < cap
        val hasMore = total > visibleCount && !stillStepping
        val isBackgroundSyncing = !controller.isSourceComplete || stillStepping
        publishState(
            controller.loadedItems.take(visibleCount),
            total,
            hasMore,
            isBackgroundSyncing
        )
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

    fun markLocationPermissionAutoPrompted() {
        _state.update { it.copy(hasAutoPromptedLocation = true) }
    }

    fun onLocationPermissionChanged(granted: Boolean) {
        _state.update { it.copy(isLocationPermissionGranted = granted) }
        if (granted) {
            preparePrivateLocationCellIfPermitted()
            refreshFriendsDistancesIfNeeded(onlyIfMissingDistanceOnLoadedItems = false)
        }
    }

    private fun refreshFriendsDistancesIfNeeded(onlyIfMissingDistanceOnLoadedItems: Boolean) {
        val uid = currentUserId
        val useCase = getFriendsCapricesUseCase ?: return
        if (uid.isBlank() || !_state.value.hasLoadedFeed) return
        val hasItemsWithCellMissingDistance = friendsController.loadedItems.any {
            it.caprice.effectiveDistanceCell(it.author.locationCell) != null && it.approximateDistanceMeters == null
        }
        val needsRefresh = if (onlyIfMissingDistanceOnLoadedItems) {
            hasItemsWithCellMissingDistance
        } else {
            !friendsController.loadedWithLocationPermission || hasItemsWithCellMissingDistance
        }
        if (!needsRefresh) return
        if (distanceRefreshJob?.isActive == true) return

        distanceRefreshJob = viewModelScope.launch(ioDispatcher) {
            val refreshed = useCase.refreshCachedDistances(uid, friendsController.visiblePageLimit)
            if (currentUserId != uid) return@launch
            if (refreshed != null) {
                friendsController.loadedWithLocationPermission = true
                applyProgressiveBatch(
                    controller = friendsController,
                    batch = refreshed,
                    uid = uid,
                    publishState = ::publishFriendsState
                )
            }
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
                refreshFriendsDistancesIfNeeded(onlyIfMissingDistanceOnLoadedItems = friendsController.loadedWithLocationPermission)
            }
        }
    }

    private fun syncUserLocationCellInBackground(uid: String, cellId: String) {
        if (uid.isBlank() || cellId.isBlank()) return
        if (syncedLocationCells[uid] == cellId) return
        val repo = userProfileRepository ?: return

        viewModelScope.launch(ioDispatcher) {
            val cachedCell = runCatching { repo.getCachedUserById(uid)?.locationCell }.getOrNull()
            if (cachedCell == cellId) {
                syncedLocationCells[uid] = cellId
                return@launch
            }
            repo.updateLocationCell(uid, cellId).onSuccess {
                syncedLocationCells[uid] = cellId
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
                            isLocationPermissionGranted = true,
                            isNearbyDiscoveryEnabled = true,
                            isPreparingNearbyLocation = false,
                            preparedNearbyCell = cellId,
                            preparedLocationCell = cellId,
                            nearbyCreationError = null
                        )
                    }
                    syncUserLocationCellInBackground(currentUserId, cellId)
                    refreshFriendsDistancesIfNeeded(onlyIfMissingDistanceOnLoadedItems = friendsController.loadedWithLocationPermission)
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
