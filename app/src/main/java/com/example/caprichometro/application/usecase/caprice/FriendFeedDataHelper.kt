package com.example.caprichometro.application.usecase.caprice

import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.FriendshipStatus
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.sortFriendsFeedItems
import com.example.caprichometro.domain.model.sortedByFriendsFeedOrder
import com.example.caprichometro.domain.repository.CapriceRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.FriendshipRepository
import com.example.caprichometro.domain.repository.UserProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

internal data class RawFriendCaprice(
    val caprice: Caprice,
    val distanceMeters: Double?,
    val fallbackAuthor: UserProfile? = null
)

internal object FriendFeedDataHelper {

    private const val MAX_CONCURRENT_FRIEND_QUERIES = 8

    suspend fun loadCachedCapricesByFriend(
        friendshipRepository: FriendshipRepository?,
        capriceRepository: CapriceRepository,
        currentUserId: String,
        perFriendLimit: Int,
        preloadedFriendIds: List<String>? = null
    ): Map<String, List<Caprice>> {
        val fRepo = friendshipRepository ?: return emptyMap()
        val rawFriendIds = if (preloadedFriendIds != null) {
            preloadedFriendIds
        } else {
            val cachedFriendships = fRepo.getCachedFriendships(currentUserId)
                .filter { it.status == FriendshipStatus.ACCEPTED }
            if (cachedFriendships.isEmpty()) return emptyMap()
            cachedFriendships.mapNotNull { friendship -> friendship.memberIDs.firstOrNull { it != currentUserId } }
        }
        if (rawFriendIds.isEmpty()) return emptyMap()

        val cachedBlockedIds = fRepo.getCachedBlockedUserIds(currentUserId).toSet()
        val candidateFriendIds = rawFriendIds
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
                        val caprices = capricesDeferred.await().filter { it.isVisibleInFriendsTo(currentUserId) }
                        if (caprices.isEmpty()) null else friendId to caprices
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    suspend fun resolveCachedFriendProfiles(
        userProfileRepository: UserProfileRepository?,
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

    suspend fun hydrateValidFriendsSlice(
        userProfileRepository: UserProfileRepository?,
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

    fun buildSortedCandidates(
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

    fun resolveDistanceMeters(
        viewerLocation: ApproximateLocation?,
        caprice: Caprice,
        author: UserProfile?
    ): Double? {
        val loc = viewerLocation ?: return null
        val cell = caprice.effectiveDistanceCell(author?.locationCell) ?: return null
        return NearbyCellGrid.distanceInMeters(loc, cell)
    }

    suspend fun resolveViewerLocationOrNull(locationRepository: DeviceLocationRepository?): ApproximateLocation? {
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
