package com.example.caprichometro.application.usecase.voting

import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.domain.repository.VoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class FeedPollResult(
    val totalVotes: Int,
    val option1Percentage: Int,
    val option2Percentage: Int,
    val winningOption: Int?,
    val option1TotalVoters: Int,
    val option2TotalVoters: Int,
    val option1VoterAvatars: List<UserProfile>,
    val option2VoterAvatars: List<UserProfile>
)

/**
 * Calcula porcentajes, ganador y hasta los tres primeros perfiles de votantes por opción
 * para encuestas cerradas mostradas en el feed.
 */
class GetFeedPollResultUseCase(
    private val voteRepository: VoteRepository,
    private val userProfileRepository: UserProfileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val MAX_AVATARS_PER_OPTION = 3
    }

    suspend fun execute(
        capriceId: String,
        knownProfiles: Map<String, UserProfile> = emptyMap()
    ): Result<FeedPollResult> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) {
            return@withContext Result.success(
                FeedPollResult(
                    totalVotes = 0,
                    option1Percentage = 0,
                    option2Percentage = 0,
                    winningOption = null,
                    option1TotalVoters = 0,
                    option2TotalVoters = 0,
                    option1VoterAvatars = emptyList(),
                    option2VoterAvatars = emptyList()
                )
            )
        }

        try {
            val votes = voteRepository.getVotes(capriceId).getOrThrow()
                .sortedBy { it.timestamp }
            val option1Votes = votes.filter { it.option == 1 }
            val option2Votes = votes.filter { it.option == 2 }
            val total = option1Votes.size + option2Votes.size

            val pct1 = if (total > 0) ((option1Votes.size * 100.0) / total).roundToInt() else 0
            val pct2 = if (total > 0) 100 - pct1 else 0
            val winner = when {
                total == 0 -> null
                option1Votes.size > option2Votes.size -> 1
                option2Votes.size > option1Votes.size -> 2
                else -> null
            }

            val topIds1 = option1Votes.map { it.voterId }.distinct().take(MAX_AVATARS_PER_OPTION)
            val topIds2 = option2Votes.map { it.voterId }.distinct().take(MAX_AVATARS_PER_OPTION)
            val missingIds = (topIds1 + topIds2)
                .distinct()
                .filter { it.isNotBlank() && !knownProfiles.containsKey(it) }

            val fetchedProfiles = if (missingIds.isEmpty()) {
                emptyMap()
            } else {
                coroutineScope {
                    missingIds.map { uid ->
                        async {
                            val profile = runCatching { userProfileRepository.getUserById(uid).getOrNull() }
                                .getOrNull()
                            uid to (profile ?: UserProfile(uid = uid, username = "?"))
                        }
                    }.awaitAll().toMap()
                }
            }

            fun resolveAvatars(ids: List<String>): List<UserProfile> = ids.map { uid ->
                knownProfiles[uid]
                    ?: fetchedProfiles[uid]
                    ?: UserProfile(uid = uid, username = "?")
            }

            Result.success(
                FeedPollResult(
                    totalVotes = total,
                    option1Percentage = pct1,
                    option2Percentage = pct2,
                    winningOption = winner,
                    option1TotalVoters = option1Votes.size,
                    option2TotalVoters = option2Votes.size,
                    option1VoterAvatars = resolveAvatars(topIds1),
                    option2VoterAvatars = resolveAvatars(topIds2)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
