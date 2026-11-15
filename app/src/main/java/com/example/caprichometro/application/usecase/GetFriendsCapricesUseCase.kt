package com.example.caprichometro.application.usecase

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.ProgressiveFeedBatch
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.sortFriendsFeedItems
import com.example.caprichometro.domain.model.sortedByFriendsFeedOrder
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class GetFriendsCapricesUseCase(
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val capriceRepository: CapriceRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val friendshipRepository: FriendshipRepository? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val locationRepository: DeviceLocationRepository? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val MAX_CONCURRENT_FRIEND_QUERIES = 8
        private const val INITIAL_FAST_BATCH_SIZE = 5
        private const val DEFAULT_PAGE_SIZE = 20
        private const val PROGRESS_EMIT_THROTTLE_MS = 150L
        private const val EARLY_BATCH_FLUSH_DELAY_MS = 200L
        private const val CACHE_LOCATION_TIMEOUT_MS = 80L
    }

    private data class RawFriendCaprice(
        val caprice: Caprice,
        val distanceMeters: Double?,
        val fallbackAuthor: UserProfile? = null
    )

    private data class FriendsPageSnapshot(
        val candidates: List<RawFriendCaprice>,
        val resolvedProfiles: ConcurrentHashMap<String, UserProfile>,
        val missingProfileIds: MutableSet<String>,
        val viewerLocation: ApproximateLocation? = null,
        val hasViewerLocation: Boolean = false
    )

    private val cachedSnapshots = ConcurrentHashMap<String, FriendsPageSnapshot>()

    suspend fun execute(
        currentUserId: String,
        perFriendLimit: Int = 20,
        totalLimit: Int = 20
    ): Result<List<CapriceFeedItem>> = withContext(defaultDispatcher) {
        try {
            val safeTotal = totalLimit.coerceAtLeast(1)
            val finalBatch = observeProgressiveFeed(
                currentUserId = currentUserId,
                initialBatchSize = safeTotal,
                perFriendLimit = perFriendLimit,
                totalLimit = safeTotal,
                includeCache = false,
                pageSize = safeTotal
            ).last()
            Result.success(finalBatch.items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Emite primero caché local (`5` ítems rápidos y el resto de la página inicial hasta `pageSize`)
     * mientras sincroniza desde servidor en segundo plano sin hidratar perfiles más allá de la
     * página inicial.
     */
    fun observeProgressiveFeed(
        currentUserId: String,
        initialBatchSize: Int = INITIAL_FAST_BATCH_SIZE,
        perFriendLimit: Int = 20,
        totalLimit: Int = 20,
        includeCache: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Flow<ProgressiveFeedBatch> = channelFlow {
        if (currentUserId.isBlank()) {
            send(ProgressiveFeedBatch(items = emptyList(), isFromCache = false, isComplete = true))
            return@channelFlow
        }

        val stepSize = initialBatchSize.coerceAtLeast(1)
        val pageLimit = pageSize.coerceAtLeast(1).coerceAtMost(totalLimit.coerceAtLeast(1))
        val viewerLocationDeferred = async { resolveViewerLocationOrNull() }

        val mutex = Mutex()
        val capricesByFriend = linkedMapOf<String, List<Pair<Caprice, UserProfile?>>>()
        val cachedProfiles = ConcurrentHashMap<String, UserProfile>()
        val resolvedServerProfiles = ConcurrentHashMap<String, UserProfile>()
        val missingServerProfiles = ConcurrentHashMap.newKeySet<String>()
        var emittedCacheBatch = false
        var lastEmittedCount = 0
        var lastEmitAtMillis = 0L

        if (includeCache && friendshipRepository != null && userProfileRepository != null) {
            val cachedByFriend = loadCachedCapricesByFriend(currentUserId, perFriendLimit)
            if (cachedByFriend.isNotEmpty()) {
                cachedByFriend.forEach { (friendId, list) ->
                    capricesByFriend[friendId] = list.map { it to null }
                }
                emittedCacheBatch = true
                lastEmittedCount = capricesByFriend.values.sumOf { it.size }
                lastEmitAtMillis = clockMillis()
                val fastLocation = withTimeoutOrNull(CACHE_LOCATION_TIMEOUT_MS) {
                    viewerLocationDeferred.await()
                }
                resolveCachedFriendProfiles(capricesByFriend.keys.toList(), cachedProfiles)
                val sortedCached = buildSortedCandidates(
                    rawByFriend = capricesByFriend,
                    viewerLocation = fastLocation,
                    totalLimit = totalLimit,
                    resolvedProfiles = cachedProfiles
                )
                val visibleTarget = minOf(pageLimit, sortedCached.size)
                var prevCursor = 0
                var cursor = minOf(stepSize, visibleTarget)
                while (cursor > 0) {
                    resolveCachedFriendProfiles(
                        friendIds = sortedCached.subList(prevCursor, cursor).map { it.caprice.userId },
                        resolvedProfiles = cachedProfiles
                    )
                    val sliceItems = sortedCached.subList(0, cursor).map { candidate ->
                        val author = cachedProfiles[candidate.caprice.userId]
                        val distance = candidate.distanceMeters
                            ?: resolveDistanceMeters(fastLocation, candidate.caprice, author)
                        CapriceFeedItem(
                            caprice = candidate.caprice,
                            author = author
                                ?: UserProfile(uid = candidate.caprice.userId, username = "usuario"),
                            approximateDistanceMeters = distance
                        )
                    }.let { items ->
                        if (fastLocation != null) sortFriendsFeedItems(items) else items
                    }
                    send(
                        ProgressiveFeedBatch(
                            items = sliceItems,
                            isFromCache = true,
                            isComplete = false,
                            totalAvailableCount = sortedCached.size,
                            hasViewerLocation = fastLocation != null
                        )
                    )
                    if (cursor >= visibleTarget) break
                    prevCursor = cursor
                    cursor = minOf(cursor + stepSize, visibleTarget)
                }
            }
        }

        var emittedFirstNetworkBatch = false

        suspend fun maybeEmitPartial(forceIfNonEmpty: Boolean = false) {
            // En refresco manual (includeCache = false) evitamos emitir un subconjunto parcial
            // de un solo amigo que encoja temporalmente la lista ya visible.
            if (!includeCache) return
            val partialSnapshot = mutex.withLock {
                val totalRaw = capricesByFriend.values.sumOf { it.size }
                if (totalRaw == 0) return@withLock null
                val now = clockMillis()
                val shouldEmitFirst = !emittedCacheBatch &&
                    !emittedFirstNetworkBatch &&
                    (totalRaw >= stepSize || forceIfNonEmpty)
                val shouldEmitUpdate = (emittedCacheBatch || emittedFirstNetworkBatch) &&
                    totalRaw != lastEmittedCount &&
                    (now - lastEmitAtMillis) >= PROGRESS_EMIT_THROTTLE_MS
                if (shouldEmitFirst || shouldEmitUpdate) {
                    emittedFirstNetworkBatch = true
                    lastEmitAtMillis = now
                    lastEmittedCount = totalRaw
                    val locationSnapshot = if (viewerLocationDeferred.isCompleted) {
                        runCatching { viewerLocationDeferred.getCompleted() }.getOrNull()
                    } else null
                    val sortingProfiles = HashMap<String, UserProfile>(cachedProfiles).apply {
                        putAll(resolvedServerProfiles)
                    }
                    val candidates = buildSortedCandidates(
                        rawByFriend = capricesByFriend,
                        viewerLocation = locationSnapshot,
                        totalLimit = totalLimit,
                        resolvedProfiles = sortingProfiles
                    )
                    candidates to locationSnapshot
                } else null
            } ?: return
            val (candidatesSnapshot, locationSnapshot) = partialSnapshot

            val batchTarget = if (!emittedCacheBatch) {
                minOf(stepSize, candidatesSnapshot.size)
            } else {
                minOf(pageLimit, candidatesSnapshot.size)
            }
            val (items, _) = hydrateValidFriendsSlice(
                candidates = candidatesSnapshot,
                limit = batchTarget,
                resolvedProfiles = resolvedServerProfiles,
                missingProfileIds = missingServerProfiles,
                viewerLocation = locationSnapshot
            )
            send(
                ProgressiveFeedBatch(
                    items = items,
                    isFromCache = false,
                    isComplete = false,
                    totalAvailableCount = candidatesSnapshot.size,
                    hasViewerLocation = locationSnapshot != null
                )
            )
        }

        val earlyFlushJob = if (includeCache) {
            launch {
                delay(EARLY_BATCH_FLUSH_DELAY_MS)
                maybeEmitPartial(forceIfNonEmpty = true)
            }
        } else {
            null
        }

        val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
        var confirmedFriendIds: Set<String> = emptySet()
        coroutineScope {
            if (friendshipRepository != null && userProfileRepository != null) {
                val ownBlockedDeferred = async {
                    runCatching {
                        friendshipRepository.getBlockedUserIds(currentUserId).getOrDefault(emptyList()).toSet()
                    }.getOrDefault(emptySet())
                }
                val acceptedFriendships = friendshipRepository.getFriendships(currentUserId)
                    .first()
                    .filter { it.status == FriendshipStatus.ACCEPTED }
                val ownBlockedIds = ownBlockedDeferred.await()

                val candidateFriendIds = acceptedFriendships
                    .mapNotNull { friendship -> friendship.memberIDs.firstOrNull { it != currentUserId } }
                    .distinct()
                    .filter { it.isNotBlank() && it !in ownBlockedIds }
                confirmedFriendIds = candidateFriendIds.toSet()

                candidateFriendIds.map { friendId ->
                    async {
                        semaphore.withPermit {
                            val blockedDeferred = async {
                                friendshipRepository.isBlocked(currentUserId, friendId).getOrNull() ?: false
                            }
                            val capricesDeferred = async {
                                capriceRepository.getCaprices(friendId, perFriendLimit).getOrDefault(emptyList())
                            }
                            if (blockedDeferred.await()) {
                                mutex.withLock { capricesByFriend.remove(friendId) }
                                return@withPermit
                            }
                            val visible = capricesDeferred.await().filter { it.isVisibleTo(currentUserId) }
                            mutex.withLock {
                                capricesByFriend[friendId] = visible.map { it to null }
                            }
                            if (visible.isNotEmpty() || !emittedCacheBatch) {
                                maybeEmitPartial()
                            }
                        }
                    }
                }.awaitAll()
            } else {
                val acceptedFriends = getAcceptedFriendsUseCase.execute(currentUserId).first()
                confirmedFriendIds = acceptedFriends.map { it.uid }.toSet()
                acceptedFriends.map { friend ->
                    async {
                        semaphore.withPermit {
                            val visible = capriceRepository.getCaprices(friend.uid, perFriendLimit)
                                .getOrDefault(emptyList())
                                .filter { it.isVisibleTo(currentUserId) }
                            mutex.withLock {
                                capricesByFriend[friend.uid] = visible.map { it to friend }
                            }
                            if (visible.isNotEmpty()) {
                                maybeEmitPartial()
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        earlyFlushJob?.cancel()
        val finalLocation = viewerLocationDeferred.await()
        val friendIdsToPrecheck = mutex.withLock {
            capricesByFriend.keys.retainAll(confirmedFriendIds)
            capricesByFriend.keys.toList()
        }
        if (includeCache) {
            resolveCachedFriendProfiles(friendIdsToPrecheck, cachedProfiles)
        }
        val finalSortingProfiles = HashMap<String, UserProfile>(cachedProfiles).apply {
            putAll(resolvedServerProfiles)
        }
        val sortedFinalCandidates = mutex.withLock {
            buildSortedCandidates(
                rawByFriend = capricesByFriend,
                viewerLocation = finalLocation,
                totalLimit = totalLimit,
                resolvedProfiles = finalSortingProfiles
            )
        }
        val (page1Items, validCandidates) = hydrateValidFriendsSlice(
            candidates = sortedFinalCandidates,
            limit = pageLimit,
            resolvedProfiles = resolvedServerProfiles,
            missingProfileIds = missingServerProfiles,
            viewerLocation = finalLocation
        )
        cachedSnapshots[currentUserId] = FriendsPageSnapshot(
            candidates = validCandidates,
            resolvedProfiles = resolvedServerProfiles,
            missingProfileIds = missingServerProfiles,
            viewerLocation = finalLocation,
            hasViewerLocation = finalLocation != null
        )
        send(
            ProgressiveFeedBatch(
                items = page1Items,
                isFromCache = false,
                isComplete = true,
                totalAvailableCount = validCandidates.size,
                hasViewerLocation = finalLocation != null
            )
        )
    }.flowOn(defaultDispatcher)

    /**
     * Recalcula y reordena en memoria (`< 50 km` / `> 50 km`) las distancias del snapshot actual
     * sin volver a consultar caprichos en Firestore (por ejemplo tras conceder permiso de ubicación).
     */
    suspend fun refreshCachedDistances(
        currentUserId: String,
        visiblePageLimit: Int = DEFAULT_PAGE_SIZE
    ): ProgressiveFeedBatch? = withContext(defaultDispatcher) {
        val snapshot = cachedSnapshots[currentUserId] ?: return@withContext null
        val viewerLocation = resolveViewerLocationOrNull() ?: return@withContext null
        val recalculated = sortedByFriendsFeedOrder(
            items = snapshot.candidates.map { candidate ->
                val authorProfile = candidate.fallbackAuthor ?: snapshot.resolvedProfiles[candidate.caprice.userId]
                val distanceMeters = resolveDistanceMeters(viewerLocation, candidate.caprice, authorProfile)
                candidate.copy(distanceMeters = distanceMeters)
            },
            distanceOf = { it.distanceMeters },
            capriceOf = { it.caprice }
        )
        val safeLimit = visiblePageLimit.coerceAtLeast(DEFAULT_PAGE_SIZE)
        val (hydratedItems, validCandidates) = hydrateValidFriendsSlice(
            candidates = recalculated,
            limit = safeLimit,
            resolvedProfiles = snapshot.resolvedProfiles,
            missingProfileIds = snapshot.missingProfileIds,
            viewerLocation = viewerLocation
        )
        cachedSnapshots[currentUserId] = snapshot.copy(
            candidates = validCandidates,
            viewerLocation = viewerLocation,
            hasViewerLocation = true
        )
        ProgressiveFeedBatch(
            items = hydratedItems,
            isFromCache = false,
            isComplete = true,
            totalAvailableCount = validCandidates.size,
            hasViewerLocation = true
        )
    }

    /**
     * Hidrata bajo demanda la siguiente página de amigos (`5` primero y el resto de `5` en `5`)
     * sin volver a consultar caprichos en Firestore.
     */
    fun observeNextPage(
        currentUserId: String,
        currentLoadedCount: Int,
        initialBatchSize: Int = INITIAL_FAST_BATCH_SIZE,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Flow<ProgressiveFeedBatch> = flow {
        val snapshot = cachedSnapshots[currentUserId]
        if (snapshot == null || currentLoadedCount >= snapshot.candidates.size) {
            val currentItems = snapshot?.let {
                hydrateValidFriendsSlice(
                    candidates = it.candidates,
                    limit = it.candidates.size,
                    resolvedProfiles = it.resolvedProfiles,
                    missingProfileIds = it.missingProfileIds,
                    viewerLocation = it.viewerLocation
                ).first
            }.orEmpty()
            emit(
                ProgressiveFeedBatch(
                    items = currentItems,
                    isFromCache = false,
                    isComplete = true,
                    totalAvailableCount = currentItems.size,
                    hasViewerLocation = snapshot?.hasViewerLocation == true
                )
            )
            return@flow
        }

        val stepSize = initialBatchSize.coerceAtLeast(1)
        var targetCount = (currentLoadedCount + stepSize).coerceAtMost(snapshot.candidates.size)
        val maxPageEnd = (currentLoadedCount + pageSize.coerceAtLeast(1)).coerceAtMost(snapshot.candidates.size)
        var workingCandidates = snapshot.candidates

        while (true) {
            val (hydratedItems, prunedCandidates) = hydrateValidFriendsSlice(
                candidates = workingCandidates,
                limit = targetCount,
                resolvedProfiles = snapshot.resolvedProfiles,
                missingProfileIds = snapshot.missingProfileIds,
                viewerLocation = snapshot.viewerLocation
            )
            workingCandidates = prunedCandidates
            cachedSnapshots[currentUserId] = snapshot.copy(candidates = prunedCandidates)

            val boundedPageEnd = maxPageEnd.coerceAtMost(prunedCandidates.size)
            val isLast = targetCount >= boundedPageEnd || hydratedItems.size >= prunedCandidates.size
            emit(
                ProgressiveFeedBatch(
                    items = hydratedItems,
                    isFromCache = false,
                    isComplete = isLast,
                    totalAvailableCount = prunedCandidates.size,
                    hasViewerLocation = snapshot.hasViewerLocation
                )
            )
            if (isLast) break
            targetCount = (targetCount + stepSize).coerceAtMost(boundedPageEnd)
        }
    }.flowOn(defaultDispatcher)

    private suspend fun loadCachedCapricesByFriend(
        currentUserId: String,
        perFriendLimit: Int
    ): Map<String, List<Caprice>> {
        val fRepo = friendshipRepository ?: return emptyMap()
        val cachedFriendships = fRepo.getCachedFriendships(currentUserId)
            .filter { it.status == FriendshipStatus.ACCEPTED }
        if (cachedFriendships.isEmpty()) return emptyMap()

        val cachedBlockedIds = fRepo.getCachedBlockedUserIds(currentUserId).toSet()
        val candidateFriendIds = cachedFriendships
            .mapNotNull { friendship -> friendship.memberIDs.firstOrNull { it != currentUserId } }
            .distinct()
            .filter { it.isNotBlank() && it !in cachedBlockedIds }
        if (candidateFriendIds.isEmpty()) return emptyMap()

        val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
        return coroutineScope {
            candidateFriendIds.map { friendId ->
                async {
                    semaphore.withPermit {
                        val blockedDeferred = async { fRepo.isCachedBlocked(currentUserId, friendId) }
                        val capricesDeferred = async { capriceRepository.getCachedCaprices(friendId, perFriendLimit) }
                        if (blockedDeferred.await()) return@withPermit null
                        val caprices = capricesDeferred.await().filter { it.isVisibleTo(currentUserId) }
                        if (caprices.isEmpty()) null else friendId to caprices
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private suspend fun resolveCachedFriendProfiles(
        friendIds: List<String>,
        resolvedProfiles: ConcurrentHashMap<String, UserProfile>
    ) {
        val uRepo = userProfileRepository ?: return
        val missingIds = friendIds.distinct().filter { it.isNotBlank() && !resolvedProfiles.containsKey(it) }
        if (missingIds.isEmpty()) return
        val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
        coroutineScope {
            missingIds.map { friendId ->
                async {
                    semaphore.withPermit {
                        uRepo.getCachedUserById(friendId)?.let { profile ->
                            resolvedProfiles[friendId] = profile
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun hydrateValidFriendsSlice(
        candidates: List<RawFriendCaprice>,
        limit: Int,
        resolvedProfiles: ConcurrentHashMap<String, UserProfile>,
        missingProfileIds: MutableSet<String>,
        viewerLocation: ApproximateLocation? = null
    ): Pair<List<CapriceFeedItem>, List<RawFriendCaprice>> {
        val uRepo = userProfileRepository
        if (uRepo == null) {
            val valid = candidates.filter { it.fallbackAuthor != null }
            val items = valid.take(limit).map { candidate ->
                val author = candidate.fallbackAuthor!!
                val distance = candidate.distanceMeters
                    ?: resolveDistanceMeters(viewerLocation, candidate.caprice, author)
                CapriceFeedItem(
                    caprice = candidate.caprice,
                    author = author,
                    approximateDistanceMeters = distance
                )
            }
            val ordered = if (viewerLocation != null) sortFriendsFeedItems(items) else items
            return ordered to valid
        }

        val working = candidates.filter { it.caprice.userId !in missingProfileIds }.toMutableList()
        while (true) {
            val window = working.take(limit)
            val unresolvedIds = window
                .map { it.caprice.userId }
                .distinct()
                .filter { !resolvedProfiles.containsKey(it) && it !in missingProfileIds }
            if (unresolvedIds.isEmpty()) break

            val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
            val newlyMissing = ConcurrentHashMap.newKeySet<String>()
            coroutineScope {
                unresolvedIds.map { friendId ->
                    async {
                        semaphore.withPermit {
                            val profile = uRepo.getUserById(friendId).getOrNull()
                            if (profile != null) {
                                resolvedProfiles[friendId] = profile
                            } else {
                                newlyMissing.add(friendId)
                            }
                        }
                    }
                }.awaitAll()
            }
            if (newlyMissing.isEmpty()) break
            missingProfileIds.addAll(newlyMissing)
            working.removeAll { it.caprice.userId in newlyMissing }
        }

        val hydrated = working.take(limit).mapNotNull { candidate ->
            val profile = resolvedProfiles[candidate.caprice.userId] ?: return@mapNotNull null
            val distance = candidate.distanceMeters
                ?: resolveDistanceMeters(viewerLocation, candidate.caprice, profile)
            CapriceFeedItem(
                caprice = candidate.caprice,
                author = profile,
                approximateDistanceMeters = distance
            )
        }
        val ordered = if (viewerLocation != null) sortFriendsFeedItems(hydrated) else hydrated
        return ordered to working
    }

    private fun buildSortedCandidates(
        rawByFriend: Map<String, List<Pair<Caprice, UserProfile?>>>,
        viewerLocation: ApproximateLocation?,
        totalLimit: Int,
        resolvedProfiles: Map<String, UserProfile> = emptyMap()
    ): List<RawFriendCaprice> {
        val seenIds = HashSet<String>()
        val raw = mutableListOf<RawFriendCaprice>()
        for (list in rawByFriend.values) {
            for ((caprice, fallbackAuthor) in list) {
                if (!seenIds.add(caprice.id)) continue
                val knownAuthor = fallbackAuthor ?: resolvedProfiles[caprice.userId]
                val distanceMeters = resolveDistanceMeters(viewerLocation, caprice, knownAuthor)
                raw.add(
                    RawFriendCaprice(
                        caprice = caprice,
                        distanceMeters = distanceMeters,
                        fallbackAuthor = fallbackAuthor
                    )
                )
            }
        }
        return sortedByFriendsFeedOrder(
            items = raw,
            distanceOf = { it.distanceMeters },
            capriceOf = { it.caprice }
        ).take(totalLimit.coerceAtLeast(1))
    }

    private fun resolveDistanceMeters(
        viewerLocation: ApproximateLocation?,
        caprice: Caprice,
        author: UserProfile?
    ): Double? {
        val loc = viewerLocation ?: return null
        val cell = caprice.effectiveDistanceCell(author?.locationCell) ?: return null
        return NearbyCellGrid.distanceInMeters(loc, cell)
    }

    private suspend fun resolveViewerLocationOrNull(): ApproximateLocation? {
        val locRepo = locationRepository ?: return null
        if (!locRepo.hasLocationPermission()) return null
        return try {
            locRepo.currentApproximateLocation().getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
