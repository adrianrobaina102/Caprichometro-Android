package com.example.caprichometro.application.usecase.notification

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import com.example.caprichometro.domain.repository.FriendCapriceUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ObservedFriendUpdate(
    val feedItem: CapriceFeedItem,
    val isFinalDecision: Boolean
)

class ObserveFriendCapricesUseCase(
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val observationRepository: CapriceObservationRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun execute(currentUserId: String, sinceTimestamp: Long): Flow<CapriceFeedItem> =
        observeFriendsFeedStream(currentUserId) { friendUid ->
            observationRepository.observeFriendCaprices(friendUid, sinceTimestamp).map {
                FriendCapriceUpdate(caprice = it, isFinalDecision = false)
            }
        }.map { it.feedItem }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFinalDecisions(currentUserId: String, sinceTimestamp: Long): Flow<CapriceFeedItem> =
        observeFriendsFeedStream(currentUserId) { friendUid ->
            observationRepository.observeFriendFinalDecisions(friendUid, sinceTimestamp).map {
                FriendCapriceUpdate(caprice = it, isFinalDecision = true)
            }
        }.map { it.feedItem }

    /**
     * Unifica en un solo flujo las altas y decisiones finales visibles para [currentUserId]
     * reduciendo a la mitad los listeners abiertos por amigo.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllUpdates(currentUserId: String, sinceTimestamp: Long): Flow<ObservedFriendUpdate> =
        observeFriendsFeedStream(currentUserId) { friendUid ->
            observationRepository.observeFriendUpdates(friendUid, sinceTimestamp)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFriendsFeedStream(
        currentUserId: String,
        streamProvider: (String) -> Flow<FriendCapriceUpdate>
    ): Flow<ObservedFriendUpdate> {
        if (currentUserId.isBlank()) return emptyFlow()

        return getAcceptedFriendsUseCase.execute(currentUserId).flatMapLatest { friends ->
            if (friends.isEmpty()) {
                emptyFlow()
            } else {
                channelFlow {
                    friends.forEach { friend ->
                        launch {
                            streamProvider(friend.uid)
                                .catch { /* Aísla fallos de un amigo concreto para no matar el flujo del resto */ }
                                .collect { update ->
                                    if (update.caprice.isVisibleInFriendsTo(currentUserId)) {
                                        send(
                                            ObservedFriendUpdate(
                                                feedItem = CapriceFeedItem(
                                                    caprice = update.caprice,
                                                    author = friend
                                                ),
                                                isFinalDecision = update.isFinalDecision
                                            )
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}
