package com.example.caprichometro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.infrastructure.data.FirestoreCapriceRepository
import com.example.caprichometro.infrastructure.data.FirestoreFriendshipRepository
import com.example.caprichometro.infrastructure.data.FirestoreUserProfileRepository
import com.example.caprichometro.infrastructure.data.FirestoreVoteRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Tests de integración reales contra Firestore Emulator (`10.0.2.2:8080`).
 * En entornos locales sin emulador de Firebase activo se omiten limpiamente mediante `Assume`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreIntegrationTest {

    companion object {
        private const val EMULATOR_HOST = "10.0.2.2"
        private const val EMULATOR_PORT = 8080
        private const val EMULATOR_APP_NAME = "firestore-emulator-integration"

        @Volatile
        private var cachedEmulatorDb: FirebaseFirestore? = null

        private fun isFirestoreEmulatorReachable(): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(EMULATOR_HOST, EMULATOR_PORT), 500)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun getEmulatorFirestore(): FirebaseFirestore {
            cachedEmulatorDb?.let { return it }
            synchronized(this) {
                cachedEmulatorDb?.let { return it }
                val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                val app = FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
                    ?: FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions.Builder()
                            .setProjectId("caprichometro-ci-stub")
                            .setApplicationId("1:000000000000:android:0000000000000000000000")
                            .setApiKey("CI-STUB-KEY")
                            .build(),
                        EMULATOR_APP_NAME
                    )
                val db = FirebaseFirestore.getInstance(app).apply {
                    useEmulator(EMULATOR_HOST, EMULATOR_PORT)
                    firestoreSettings = FirebaseFirestoreSettings.Builder().build()
                }
                cachedEmulatorDb = db
                return db
            }
        }
    }

    private lateinit var db: FirebaseFirestore

    @Before
    fun setUp() {
        assumeTrue(
            "Firestore Emulator no disponible en $EMULATOR_HOST:$EMULATOR_PORT",
            isFirestoreEmulatorReachable()
        )
        db = getEmulatorFirestore()
    }

    @Test
    fun userProfile_saveProfileReservesUsernameAtomicallyAndUpdatesPartialFields() = runBlocking {
        val repo = FirestoreUserProfileRepository(db)
        val suffix = UUID.randomUUID().toString().take(8)
        val uid1 = "u1_$suffix"
        val uid2 = "u2_$suffix"
        val username = "user_$suffix"

        val saveFirst = repo.saveProfile(UserProfile(uid = uid1, username = username, bio = "Hola"))
        assertTrue(saveFirst.isSuccess)

        val duplicateUsername = repo.saveProfile(UserProfile(uid = uid2, username = username))
        assertTrue(duplicateUsername.isFailure)

        assertTrue(repo.updateBio(uid1, "Bio actualizada").isSuccess)
        assertTrue(repo.updateLocationCell(uid1, "v1_2608_3525").isSuccess)

        val loaded = repo.getUserById(uid1).getOrThrow()
        assertNotNull(loaded)
        assertEquals(username, loaded!!.username)
        assertEquals("Bio actualizada", loaded.bio)
        assertEquals("v1_2608_3525", loaded.locationCell)
    }

    @Test
    fun friendship_requestAcceptAndBidirectionalBlockWorkAtomically() = runBlocking {
        val repo = FirestoreFriendshipRepository(db)
        val suffix = UUID.randomUUID().toString().take(8)
        val uidA = "a_$suffix"
        val uidB = "b_$suffix"
        val canonicalId = listOf(uidA, uidB).sorted().joinToString("_")

        val sendResult = repo.sendFriendRequest(
            Friendship(
                id = canonicalId,
                memberIDs = listOf(uidA, uidB),
                requestedBy = uidA,
                status = FriendshipStatus.PENDING
            )
        )
        assertTrue(sendResult.isSuccess)
        assertTrue(repo.updateFriendshipStatus(canonicalId, FriendshipStatus.ACCEPTED).isSuccess)

        assertTrue(repo.blockUser(uidA, uidB).isSuccess)
        assertTrue(repo.isBlocked(uidA, uidB).getOrThrow())
        assertTrue(repo.isBlocked(uidB, uidA).getOrThrow())

        assertTrue(repo.unblockUser(uidA, uidB).isSuccess)
        assertFalse(repo.isBlocked(uidA, uidB).getOrThrow())
    }

    @Test
    fun capriceAndVotes_saveWithOptionalPriceAndReadBackAccurately() = runBlocking {
        val capriceRepo = FirestoreCapriceRepository(db)
        val voteRepo = FirestoreVoteRepository(db)
        val suffix = UUID.randomUUID().toString().take(8)
        val authorId = "author_$suffix"
        val voterId = "voter_$suffix"

        val caprice = Caprice.Builder()
            .setUserId(authorId)
            .setType("Compra")
            .setQuestion("¿Zapatillas blancas o negras?")
            .setOptions("Blancas", "Negras")
            .setPrice(59.95)
            .setVisibleToUserIds(listOf(authorId, voterId))
            .setNearbyCell("v1_2608_3525")
            .build()

        val createdId = capriceRepo.save(caprice).getOrThrow()
        assertTrue(createdId.isNotBlank())

        assertTrue(voteRepo.castVote(Vote(capriceId = createdId, voterId = voterId, option = 1)).isSuccess)
        val votes = voteRepo.getVotes(createdId).getOrThrow()
        assertEquals(1, votes.size)
        assertEquals(1, votes.first().option)

        assertTrue(capriceRepo.updateFinalDecision(createdId, decision = 1, decidedAt = 9_999L).isSuccess)
        val loaded = capriceRepo.getCaprices(authorId, limit = 10).getOrThrow().first { it.id == createdId }
        assertEquals(59.95, loaded.price!!, 0.001)
        assertEquals(1, loaded.finalDecision)
        assertEquals(9_999L, loaded.finalDecisionAt)
    }
}
