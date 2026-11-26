package com.example.caprichometro.infrastructure.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.caprice.GetCapricesUseCase
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetBlockedUsersUseCase
import com.example.caprichometro.application.usecase.friendship.UnblockUserUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.profile.UpdateBioUseCase
import com.example.caprichometro.application.usecase.profile.UpdateProfilePhotoUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceCacheControl
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.domain.repository.CapriceMediaRepository
import com.example.caprichometro.domain.repository.VoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

private enum class CachedVerdict { ACCEPTED, REJECTED, DECIDED_TIE, NONE }

private data class CachedVerdictEntry(
    val finalDecision: Int?,
    val verdict: CachedVerdict
)

data class ProfileState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val email: String = "",
    val caprices: List<Caprice> = emptyList(),
    val hasMoreCaprices: Boolean = false,
    val isLoadingMoreCaprices: Boolean = false,
    val friends: List<UserProfile> = emptyList(),
    val blockedUsers: List<UserProfile> = emptyList(),
    val verdicts: Map<String, Boolean?> = emptyMap(), // capriceId -> true (siguió al ganador), false (no siguió), null (empate/sin votos/activa)
    val decidedTieIds: Set<String> = emptySet(),
    val voteCounts: Map<String, Int> = emptyMap(),
    val capriceImages: Map<String, String> = emptyMap(),
    val isOwnProfile: Boolean = true,
    val isSavingBio: Boolean = false,
    val bioErrorMessage: String? = null,
    val isSavingPhoto: Boolean = false,
    val photoErrorMessage: String? = null,
    val isDeletingAccount: Boolean = false,
    val isPerformingFriendAction: Boolean = false,
    val unblockingUserIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val isSignedOut: Boolean = false
)

private data class ProfileLoadResult(
    val profile: UserProfile?,
    val caprices: List<Caprice>,
    val hasMoreCaprices: Boolean,
    val blockedUsers: List<UserProfile>,
    val friends: List<UserProfile>,
    val verdicts: Map<String, Boolean?>,
    val decidedTieIds: Set<String>,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    private val unblockUserUseCase: UnblockUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val getCapricesUseCase: GetCapricesUseCase,
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val voteRepository: VoteRepository? = null,
    private val updateBioUseCase: UpdateBioUseCase = UpdateBioUseCase(userProfileRepository),
    private val removeFriendUseCase: RemoveFriendUseCase? = null,
    private val blockUserUseCase: BlockUserUseCase? = null,
    private val updateProfilePhotoUseCase: UpdateProfilePhotoUseCase = UpdateProfilePhotoUseCase(userProfileRepository),
    private val capriceCacheControl: CapriceCacheControl? = null,
    private val capriceMediaRepository: CapriceMediaRepository? = null
) : ViewModel() {

    companion object {
        const val PROFILE_CAPRICES_PAGE_SIZE = 18
    }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    // Cache en memoria thread-safe para veredictos de dudas cerradas (se invalida si cambia finalDecision)
    private val verdictCache = ConcurrentHashMap<String, CachedVerdictEntry>()
    private val voteCountCache = ConcurrentHashMap<String, Int>()
    private val requestedImageIds = ConcurrentHashMap.newKeySet<String>()
    private val imageLoadJobs = ConcurrentHashMap<String, Job>()
    private val imageLoadSemaphore = Semaphore(4)
    private var sessionGeneration = 0L
    private var loadedTargetUserId: String = ""
    private var loadedCurrentUserId: String = ""
    private var currentCapriceLimit: Int = PROFILE_CAPRICES_PAGE_SIZE
    private var profileLoadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var verdictsJob: Job? = null

    fun reset() {
        sessionGeneration++
        profileLoadJob?.cancel()
        profileLoadJob = null
        loadMoreJob?.cancel()
        loadMoreJob = null
        verdictsJob?.cancel()
        verdictsJob = null
        loadedTargetUserId = ""
        loadedCurrentUserId = ""
        currentCapriceLimit = PROFILE_CAPRICES_PAGE_SIZE
        _state.value = ProfileState()
        verdictCache.clear()
        voteCountCache.clear()
        requestedImageIds.clear()
        imageLoadJobs.values.forEach { it.cancel() }
        imageLoadJobs.clear()
    }

    fun loadCapriceImageIfNeeded(caprice: Caprice) {
        val repo = capriceMediaRepository ?: return
        if (caprice.id.isBlank() || !caprice.hasImage || caprice.imageUrl.isNotBlank()) return
        if (_state.value.capriceImages.containsKey(caprice.id)) return
        if (!requestedImageIds.add(caprice.id)) return

        val requestSession = sessionGeneration
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                imageLoadSemaphore.withPermit {
                    if (requestSession != sessionGeneration) return@withPermit
                    repo.getCapriceImageUrl(caprice.id)
                        .onSuccess { url ->
                            if (requestSession != sessionGeneration) return@onSuccess
                            if (!url.isNullOrBlank()) {
                                _state.update { current ->
                                    val updated = LinkedHashMap(current.capriceImages)
                                    updated[caprice.id] = url
                                    while (updated.size > 54) {
                                        val eldestKey = updated.entries.firstOrNull()?.key ?: break
                                        updated.remove(eldestKey)
                                        requestedImageIds.remove(eldestKey)
                                    }
                                    current.copy(capriceImages = updated)
                                }
                            }
                        }
                        .onFailure {
                            requestedImageIds.remove(caprice.id)
                        }
                }
            } finally {
                imageLoadJobs.remove(caprice.id)
            }
        }
        imageLoadJobs[caprice.id] = job
    }

    private fun computeVerdictStatus(caprice: Caprice, votes: List<com.example.caprichometro.domain.model.Vote>): CachedVerdict {
        val decision = caprice.finalDecision ?: return CachedVerdict.NONE
        val uniqueVotes = votes.distinctBy { vote -> vote.voterId.ifBlank { "anon:${System.identityHashCode(vote)}" } }
        val opt1Votes = uniqueVotes.count { it.option == 1 }
        val opt2Votes = uniqueVotes.count { it.option == 2 }
        val winningOption = when {
            opt1Votes > opt2Votes -> 1
            opt2Votes > opt1Votes -> 2
            else -> return CachedVerdict.DECIDED_TIE
        }
        return if (decision == winningOption) CachedVerdict.ACCEPTED else CachedVerdict.REJECTED
    }

    private fun CachedVerdict.toAcceptedOrNull(): Boolean? = when (this) {
        CachedVerdict.ACCEPTED -> true
        CachedVerdict.REJECTED -> false
        CachedVerdict.DECIDED_TIE, CachedVerdict.NONE -> null
    }

    fun loadProfile(targetUserId: String, currentUserId: String = targetUserId) {
        if (targetUserId.isBlank()) return

        profileLoadJob?.cancel()
        loadMoreJob?.cancel()
        verdictsJob?.cancel()

        val isOwn = targetUserId == currentUserId
        val email = if (isOwn) getCurrentUserUseCase.execute()?.email.orEmpty() else ""

        val isDifferentUser = _state.value.profile?.uid != targetUserId
        if (isDifferentUser) {
            sessionGeneration++
            currentCapriceLimit = PROFILE_CAPRICES_PAGE_SIZE
        }
        loadedTargetUserId = targetUserId
        loadedCurrentUserId = currentUserId
        val requestSession = sessionGeneration
        val requestedLimit = currentCapriceLimit

        _state.update {
            it.copy(
                isLoading = true,
                profile = if (isDifferentUser) null else it.profile,
                caprices = if (isDifferentUser) emptyList() else it.caprices,
                hasMoreCaprices = if (isDifferentUser) false else it.hasMoreCaprices,
                isLoadingMoreCaprices = false,
                friends = if (isDifferentUser || !isOwn) emptyList() else it.friends,
                blockedUsers = if (isDifferentUser || !isOwn) emptyList() else it.blockedUsers,
                verdicts = if (isDifferentUser) emptyMap() else it.verdicts,
                decidedTieIds = if (isDifferentUser) emptySet() else it.decidedTieIds,
                email = email,
                isOwnProfile = isOwn,
                isSignedOut = false,
                errorMessage = null
            )
        }

        profileLoadJob = viewModelScope.launch {
            // Invalidar caché del perfil visitado para reflejar cambios recientes de foto o biografía
            (userProfileRepository as? ProfileCacheControl)?.invalidateUser(targetUserId)

            // Fase 1: Cargar datos principales en paralelo (~200ms) sin bloquear por subcolecciones de votos
            val result = coroutineScope {
                val profileDeferred = async { userProfileRepository.getUserById(targetUserId) }
                val capricesDeferred = async { getCapricesUseCase.execute(targetUserId, limit = requestedLimit + 1) }
                val blockedDeferred = async {
                    if (isOwn) {
                        getBlockedUsersUseCase.execute(targetUserId).getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
                val friendsDeferred = async {
                    if (isOwn) {
                        try {
                            getAcceptedFriendsUseCase.execute(targetUserId)
                                .catch { emit(emptyList()) }
                                .first()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }

                val profileResult = profileDeferred.await()
                val capricesResult = capricesDeferred.await()
                val rawCaprices = capricesResult.getOrDefault(emptyList())
                val loadError = profileResult.exceptionOrNull()?.message
                    ?: capricesResult.exceptionOrNull()?.message
                val hasMoreCaprices = rawCaprices.size > requestedLimit
                val pageCaprices = rawCaprices.take(requestedLimit)
                val loadedCaprices = if (isOwn) {
                    pageCaprices
                } else {
                    pageCaprices.filter { it.isVisibleTo(currentUserId) }
                }
                val validCached = loadedCaprices.mapNotNull { caprice ->
                    verdictCache[caprice.id]
                        ?.takeIf { it.finalDecision == caprice.finalDecision }
                        ?.let { caprice.id to it.verdict }
                }
                val cachedVerdicts = validCached.associate { it.first to it.second.toAcceptedOrNull() }
                val cachedTieIds = validCached
                    .filter { it.second == CachedVerdict.DECIDED_TIE }
                    .map { it.first }
                    .toSet()

                ProfileLoadResult(
                    profile = profileResult.getOrNull(),
                    caprices = loadedCaprices,
                    hasMoreCaprices = hasMoreCaprices,
                    blockedUsers = blockedDeferred.await(),
                    friends = friendsDeferred.await(),
                    verdicts = cachedVerdicts,
                    decidedTieIds = cachedTieIds,
                    errorMessage = loadError
                )
            }

            if (requestSession != sessionGeneration) return@launch

            _state.update {
                it.copy(
                    isLoading = false,
                    profile = result.profile,
                    caprices = result.caprices,
                    hasMoreCaprices = result.hasMoreCaprices,
                    friends = result.friends,
                    blockedUsers = result.blockedUsers,
                    verdicts = result.verdicts,
                    decidedTieIds = result.decidedTieIds,
                    errorMessage = result.errorMessage ?: it.errorMessage
                )
            }

            // Fase 2: Cargar en segundo plano solo los veredictos cerrados no cacheados (pool de 4)
            loadMissingVerdicts(result.caprices, requestSession)
        }
    }

    fun loadMoreCaprices() {
        val targetId = loadedTargetUserId
        if (targetId.isBlank() || _state.value.isLoading || _state.value.isLoadingMoreCaprices || !_state.value.hasMoreCaprices) {
            return
        }
        val requestSession = sessionGeneration
        val nextLimit = currentCapriceLimit + PROFILE_CAPRICES_PAGE_SIZE
        val currentUserId = loadedCurrentUserId
        val isOwn = targetId == currentUserId

        _state.update { it.copy(isLoadingMoreCaprices = true, errorMessage = null) }
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            val result = getCapricesUseCase.execute(targetId, limit = nextLimit + 1)
            if (requestSession != sessionGeneration) return@launch

            result.fold(
                onSuccess = { rawCaprices ->
                    val hasMore = rawCaprices.size > nextLimit
                    val pageCaprices = rawCaprices.take(nextLimit)
                    val loadedCaprices = if (isOwn) {
                        pageCaprices
                    } else {
                        pageCaprices.filter { it.isVisibleTo(currentUserId) }
                    }
                    currentCapriceLimit = nextLimit

                    _state.update { current ->
                        current.copy(
                            caprices = loadedCaprices,
                            hasMoreCaprices = hasMore,
                            isLoadingMoreCaprices = false
                        )
                    }
                    loadMissingVerdicts(loadedCaprices, requestSession)
                },
                onFailure = { error ->
                    _state.update { current ->
                        current.copy(
                            isLoadingMoreCaprices = false,
                            errorMessage = error.message ?: "No se pudieron cargar más recuerdos"
                        )
                    }
                }
            )
        }
    }

    private fun loadMissingVerdicts(caprices: List<Caprice>, requestSession: Long) {
        val repo = voteRepository ?: return
        val pendingCaprices = caprices.filter { caprice ->
            val isExpired = PollTiming.isPollExpired(caprice.timestamp)
            val hasValidVerdict = verdictCache[caprice.id]?.finalDecision == caprice.finalDecision
            isExpired && (!hasValidVerdict || !voteCountCache.containsKey(caprice.id))
        }
        if (pendingCaprices.isEmpty()) {
            val cachedCounts = caprices.mapNotNull { c ->
                voteCountCache[c.id]?.let { c.id to it }
            }.toMap()
            if (cachedCounts.isNotEmpty()) {
                _state.update { current -> current.copy(voteCounts = current.voteCounts + cachedCounts) }
            }
            return
        }

        verdictsJob?.cancel()
        verdictsJob = viewModelScope.launch {
            val semaphore = Semaphore(4)
            val computed = coroutineScope {
                pendingCaprices.map { caprice ->
                    async {
                        semaphore.withPermit {
                            val votes = repo.getVotes(caprice.id).getOrNull()
                                ?: return@withPermit null
                            val cachedVerdict = computeVerdictStatus(caprice, votes)
                            if (caprice.finalDecision != null) {
                                verdictCache[caprice.id] = CachedVerdictEntry(caprice.finalDecision, cachedVerdict)
                            }
                            voteCountCache[caprice.id] = votes.size
                            Triple(caprice.id, cachedVerdict, votes.size)
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (requestSession != sessionGeneration) return@launch

            val computedVerdicts = computed.associate { it.first to it.second.toAcceptedOrNull() }
            val computedTieIds = computed.filter { it.second == CachedVerdict.DECIDED_TIE }.map { it.first }.toSet()
            val recomputedNonTieIds = computed.filter { it.second != CachedVerdict.DECIDED_TIE }.map { it.first }.toSet()
            val computedCounts = computed.associate { it.first to it.third }
            _state.update { current ->
                current.copy(
                    verdicts = current.verdicts + computedVerdicts,
                    decidedTieIds = (current.decidedTieIds - recomputedNonTieIds) + computedTieIds,
                    voteCounts = current.voteCounts + computedCounts
                )
            }
        }
    }

    fun clearBioError() {
        _state.update { it.copy(bioErrorMessage = null) }
    }

    fun markFinalDecision(capriceId: String, decision: Int, decidedAt: Long) {
        var updatedCaprice: Caprice? = null
        _state.update { current ->
            current.copy(
                caprices = current.caprices.map { caprice ->
                    if (caprice.id == capriceId) {
                        caprice.copy(finalDecision = decision, finalDecisionAt = decidedAt).also {
                            updatedCaprice = it
                        }
                    } else caprice
                }
            )
        }
        val targetCaprice = updatedCaprice ?: return
        val repo = voteRepository ?: return
        val requestSession = sessionGeneration
        viewModelScope.launch {
            val votes = repo.getVotes(capriceId).getOrNull() ?: return@launch
            if (requestSession != sessionGeneration) return@launch
            val cachedVerdict = computeVerdictStatus(targetCaprice, votes)
            verdictCache[capriceId] = CachedVerdictEntry(targetCaprice.finalDecision, cachedVerdict)
            _state.update { current ->
                if (requestSession != sessionGeneration) return@update current
                val updatedTieIds = if (cachedVerdict == CachedVerdict.DECIDED_TIE) {
                    current.decidedTieIds + capriceId
                } else {
                    current.decidedTieIds - capriceId
                }
                current.copy(
                    verdicts = current.verdicts + (capriceId to cachedVerdict.toAcceptedOrNull()),
                    decidedTieIds = updatedTieIds
                )
            }
        }
    }

    fun updateBio(userId: String, newBio: String, onSuccess: () -> Unit = {}) {
        if (userId.isBlank() || _state.value.isSavingBio) return
        val requestSession = sessionGeneration
        _state.update { it.copy(isSavingBio = true, bioErrorMessage = null) }

        viewModelScope.launch {
            val result = updateBioUseCase.execute(userId, newBio)
            // Descartar respuestas tardías si se cerró sesión o cambió la cuenta activa
            val currentLoadedUid = _state.value.profile?.uid
            if (requestSession != sessionGeneration || (currentLoadedUid != null && currentLoadedUid != userId)) {
                return@launch
            }
            result.fold(
                onSuccess = {
                    val trimmed = newBio.trim()
                    _state.update { current ->
                        current.copy(
                            isSavingBio = false,
                            bioErrorMessage = null,
                            profile = current.profile?.copy(bio = trimmed)
                        )
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSavingBio = false,
                            bioErrorMessage = error.message ?: "No se pudo actualizar la biografía."
                        )
                    }
                }
            )
        }
    }

    fun clearPhotoError() {
        _state.update { it.copy(photoErrorMessage = null) }
    }

    fun updateProfilePhoto(userId: String, photoUrl: String, onSuccess: () -> Unit = {}) {
        if (userId.isBlank() || _state.value.isSavingPhoto) return
        val requestSession = sessionGeneration
        _state.update { it.copy(isSavingPhoto = true, photoErrorMessage = null) }

        viewModelScope.launch {
            val result = updateProfilePhotoUseCase.execute(userId, photoUrl)
            val currentLoadedUid = _state.value.profile?.uid
            if (requestSession != sessionGeneration || (currentLoadedUid != null && currentLoadedUid != userId)) {
                return@launch
            }
            result.fold(
                onSuccess = {
                    val trimmed = photoUrl.trim()
                    _state.update { current ->
                        current.copy(
                            isSavingPhoto = false,
                            photoErrorMessage = null,
                            profile = current.profile?.copy(photoUrl = trimmed)
                        )
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSavingPhoto = false,
                            photoErrorMessage = error.message ?: "No se pudo actualizar la foto de perfil."
                        )
                    }
                }
            )
        }
    }

    fun unblockUser(currentUserId: String, blockedUserId: String) {
        if (currentUserId.isBlank() || blockedUserId.isBlank() || blockedUserId in _state.value.unblockingUserIds) return
        val requestSession = sessionGeneration
        _state.update { it.copy(unblockingUserIds = it.unblockingUserIds + blockedUserId, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = unblockUserUseCase.execute(currentUserId, blockedUserId)
                if (requestSession != sessionGeneration) return@launch
                result.fold(
                    onSuccess = {
                        _state.update { current ->
                            current.copy(
                                blockedUsers = current.blockedUsers.filterNot { it.uid == blockedUserId }
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(errorMessage = error.message ?: "No se pudo desbloquear al usuario") }
                    }
                )
            } finally {
                if (requestSession == sessionGeneration) {
                    _state.update { it.copy(unblockingUserIds = it.unblockingUserIds - blockedUserId) }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase.execute()
            (userProfileRepository as? ProfileCacheControl)?.invalidateAll()
            capriceCacheControl?.invalidateAll()
            reset()
            _state.update { it.copy(isSignedOut = true) }
        }
    }

    fun deleteAccount(currentUserId: String, username: String) {
        if (currentUserId.isBlank() || _state.value.isDeletingAccount) return

        _state.update { it.copy(isDeletingAccount = true, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = deleteAccountUseCase.execute(currentUserId, username)
            result.fold(
                onSuccess = {
                    (userProfileRepository as? ProfileCacheControl)?.invalidateAll()
                    capriceCacheControl?.invalidateAll()
                    reset()
                    _state.update { it.copy(isSignedOut = true) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isDeletingAccount = false,
                            isLoading = false,
                            errorMessage = error.message ?: "Error al eliminar la cuenta"
                        )
                    }
                }
            )
        }
    }

    fun removeFriend(currentUserId: String, friendUserId: String, onSuccess: () -> Unit = {}) {
        if (currentUserId.isBlank() || friendUserId.isBlank() || _state.value.isPerformingFriendAction) return
        val requestSession = sessionGeneration
        viewModelScope.launch {
            _state.update { it.copy(isPerformingFriendAction = true, errorMessage = null) }
            val result = removeFriendUseCase?.execute(currentUserId, friendUserId) ?: Result.success(Unit)
            if (requestSession != sessionGeneration) return@launch
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isPerformingFriendAction = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update { it.copy(isPerformingFriendAction = false, errorMessage = error.message ?: "Error al eliminar amigo") }
                }
            )
        }
    }

    fun blockUser(currentUserId: String, targetUserId: String, onSuccess: () -> Unit = {}) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || _state.value.isPerformingFriendAction) return
        val requestSession = sessionGeneration
        viewModelScope.launch {
            _state.update { it.copy(isPerformingFriendAction = true, errorMessage = null) }
            val result = blockUserUseCase?.execute(currentUserId, targetUserId) ?: Result.success(Unit)
            if (requestSession != sessionGeneration) return@launch
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isPerformingFriendAction = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update { it.copy(isPerformingFriendAction = false, errorMessage = error.message ?: "Error al bloquear usuario") }
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
