package com.example.caprichometro.application.usecase

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.CapriceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class GetFriendsCapricesUseCase(
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val capriceRepository: CapriceRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val MAX_CONCURRENT_FRIEND_QUERIES = 4
    }

    suspend fun execute(
        currentUserId: String,
        perFriendLimit: Int = 20,
        totalLimit: Int = 20
    ): Result<List<CapriceFeedItem>> = withContext(defaultDispatcher) {
        runCatching {
            if (currentUserId.isBlank()) return@runCatching emptyList()

            val acceptedFriends = getAcceptedFriendsUseCase.execute(currentUserId).first()
            if (acceptedFriends.isEmpty()) return@runCatching emptyList()

            val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
            val allItems = coroutineScope {
                acceptedFriends.map { friend ->
                    async(defaultDispatcher) {
                        semaphore.withPermit {
                            val caprices = capriceRepository.getCaprices(friend.uid, perFriendLimit)
                                .getOrDefault(emptyList())
                                .filter { it.isVisibleTo(currentUserId) }
                            caprices.map { caprice ->
                                CapriceFeedItem(caprice = caprice, author = friend)
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            allItems
                .sortedByDescending { it.caprice.timestamp }
                .take(totalLimit)
        }
    }
}
