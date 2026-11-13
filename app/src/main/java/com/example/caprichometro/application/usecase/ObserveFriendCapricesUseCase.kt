package com.example.caprichometro.application.usecase

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class ObserveFriendCapricesUseCase(
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val observationRepository: CapriceObservationRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun execute(currentUserId: String, sinceTimestamp: Long): Flow<CapriceFeedItem> =
        observeFriendsStream(currentUserId) { friendUid ->
            observationRepository.observeFriendCaprices(friendUid, sinceTimestamp)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFinalDecisions(currentUserId: String, sinceTimestamp: Long): Flow<CapriceFeedItem> =
        observeFriendsStream(currentUserId) { friendUid ->
            observationRepository.observeFriendFinalDecisions(friendUid, sinceTimestamp)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFriendsStream(
        currentUserId: String,
        streamProvider: (String) -> Flow<Caprice>
    ): Flow<CapriceFeedItem> {
        if (currentUserId.isBlank()) return emptyFlow()

        return getAcceptedFriendsUseCase.execute(currentUserId).flatMapLatest { friends ->
            if (friends.isEmpty()) {
                emptyFlow()
            } else {
                channelFlow {
                    friends.forEach { friend ->
                        launch {
                            streamProvider(friend.uid).collect { caprice ->
                                if (caprice.isVisibleTo(currentUserId)) {
                                    send(CapriceFeedItem(caprice = caprice, author = friend))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
