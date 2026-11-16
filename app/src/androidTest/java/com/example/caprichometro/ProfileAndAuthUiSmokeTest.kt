package com.example.caprichometro

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.infrastructure.ui.HomeFeedTab
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.home.FriendsLocationPermissionBanner
import com.example.caprichometro.infrastructure.ui.home.HomeFeedTabBar
import com.example.caprichometro.infrastructure.ui.profile.ProfileMemoriesTab
import com.example.caprichometro.infrastructure.ui.profile.ProfileScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileStatsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileAndAuthUiSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileMemoriesTab_showsPendingBadgeTieVerifiedSealAndClick() {
        val now = System.currentTimeMillis()
        val expiredTimestamp = now - PollTiming.POLL_DURATION_MILLIS - 60_000L
        val pendingCaprice = Caprice(
            id = "c_pend",
            userId = "user_1",
            type = "Compra",
            question = "¿Pendiente de elegir?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = expiredTimestamp
        )
        val tieDecidedCaprice = Caprice(
            id = "c_tie",
            userId = "user_1",
            type = "Plan",
            question = "¿Café o té?",
            answer1 = "Café",
            answer2 = "Té",
            timestamp = expiredTimestamp - 10_000L,
            finalDecision = 1,
            finalDecisionAt = now - 5_000L
        )
        var selectedCaprice: Caprice? = null

        composeTestRule.setContent {
            ProfileMemoriesTab(
                caprices = listOf(pendingCaprice, tieDecidedCaprice),
                verdicts = emptyMap(),
                decidedTieIds = setOf("c_tie"),
                voteCounts = mapOf("c_pend" to 2, "c_tie" to 2),
                isOwnProfile = true,
                onSelectCaprice = { selectedCaprice = it }
            )
        }

        composeTestRule.onNodeWithText("Pendiente").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Decisión guardada en empate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ver mis recuerdos").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Café o té?").performClick()
        assertEquals("c_tie", selectedCaprice?.id)
    }

    @Test
    fun profileStatsTab_displaysHiceCasoPercentageAndCategories() {
        composeTestRule.setContent {
            ProfileStatsTab(
                hiceCasoPercent = 75,
                totalResueltas = 3,
                totalDudas = 4,
                comprasCount = 2,
                planesCount = 1,
                ropaCount = 1,
                otrosCount = 0
            )
        }

        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hice caso").assertIsDisplayed()
        composeTestRule.onNodeWithText("de mis amigos").assertIsDisplayed()
        composeTestRule.onNodeWithText("en 3 de 4 dudas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mis categorías").assertIsDisplayed()
        composeTestRule.onNodeWithText("Compras").assertIsDisplayed()
        composeTestRule.onNodeWithText("Planes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ropa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Otros").assertIsDisplayed()
    }

    @Test
    fun profileStatsTab_verTodasOpensSheetFiltersByCategoryAndSelectsCaprice() {
        val now = System.currentTimeMillis()
        val caprices = listOf(
            Caprice("c1", "user_1", "Compra", "¿Compro la mochila?", "Sí", "No", timestamp = now - 60_000L),
            Caprice("c2", "user_1", "Plan", "¿Vamos a la playa?", "Vamos", "Paso", timestamp = now - 120_000L),
            Caprice("c3", "user_1", "Ropa", "¿Me llevo la chaqueta?", "Sí", "No", timestamp = now - 180_000L)
        )
        var selected: Caprice? = null

        composeTestRule.setContent {
            ProfileStatsTab(
                hiceCasoPercent = 0,
                totalResueltas = 0,
                totalDudas = 3,
                comprasCount = 1,
                planesCount = 1,
                ropaCount = 1,
                otrosCount = 0,
                caprices = caprices,
                onSelectCaprice = { selected = it }
            )
        }

        composeTestRule.onNodeWithText("Ver todas").performClick()
        composeTestRule.onNodeWithText("Tus dudas por categoría").assertIsDisplayed()
        composeTestRule.onNodeWithText("Todas (3)").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Compro la mochila?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Vamos a la playa?").assertIsDisplayed()

        composeTestRule.onNodeWithText("Planes (1)").performClick()
        composeTestRule.onNodeWithText("¿Vamos a la playa?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Compro la mochila?").assertDoesNotExist()

        composeTestRule.onNodeWithText("Otros (0)").performScrollTo().performClick()
        composeTestRule.onNodeWithText("No hay dudas en esta categoría.").assertIsDisplayed()

        composeTestRule.onNodeWithText("Planes (1)").performScrollTo().performClick()
        composeTestRule.onNodeWithText("¿Vamos a la playa?").performClick()
        assertEquals("c2", selected?.id)
        composeTestRule.onNodeWithText("Tus dudas por categoría").assertDoesNotExist()
    }

    @Test
    fun profileStatsTab_tappingCategoryOpensSheetPreFiltered() {
        val now = System.currentTimeMillis()
        val caprices = listOf(
            Caprice("c1", "user_1", "Compra", "¿Compro la mochila?", "Sí", "No", timestamp = now - 60_000L),
            Caprice("c2", "user_1", "Ropa", "¿Me llevo la chaqueta?", "Sí", "No", timestamp = now - 180_000L)
        )

        composeTestRule.setContent {
            ProfileStatsTab(
                hiceCasoPercent = 0,
                totalResueltas = 0,
                totalDudas = 2,
                comprasCount = 1,
                planesCount = 0,
                ropaCount = 1,
                otrosCount = 0,
                caprices = caprices
            )
        }

        composeTestRule.onNodeWithText("Ropa").performClick()
        composeTestRule.onNodeWithText("¿Me llevo la chaqueta?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Compro la mochila?").assertDoesNotExist()
    }

    @Test
    fun profileScreen_ownProfileSwitchesToStatsAndOpensSettingsSheet() {
        val now = System.currentTimeMillis()
        val closedTs = now - PollTiming.POLL_DURATION_MILLIS - 60_000L
        val ownProfile = UserProfile(uid = "user_1", username = "adri", bio = "Bio de prueba")
        val friendProfile = UserProfile(uid = "f1", username = "ana")
        val tieCaprice = Caprice(
            id = "c1",
            userId = "user_1",
            type = "Plan",
            question = "¿Cine o paseo?",
            answer1 = "Cine",
            answer2 = "Paseo",
            timestamp = closedTs,
            finalDecision = 1,
            finalDecisionAt = closedTs + 350_000L
        )
        val voteRepo = FakeUiVoteRepository(
            mapOf(
                "c1" to listOf(
                    Vote("c1", "f1", 1, 1L),
                    Vote("c1", "f2", 2, 2L)
                )
            )
        )
        val viewModel = UiSmokeTestFactories.createProfileViewModel(
            authRepo = FakeUiAuthRepository(User("user_1", "adri@test.com", "adri")),
            profileRepo = FakeUiUserProfileRepository(listOf(ownProfile, friendProfile)),
            capriceRepo = FakeUiCapriceRepository(listOf(tieCaprice)),
            friendshipRepo = FakeUiFriendshipRepository(
                initialFriendships = listOf(
                    Friendship("f1_user_1", listOf("f1", "user_1"), "user_1", FriendshipStatus.ACCEPTED)
                )
            ),
            voteRepo = voteRepo
        )

        composeTestRule.setContent {
            ProfileScreen(
                viewModel = viewModel,
                targetUserId = "user_1",
                currentUserId = "user_1",
                onSignedOut = {}
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            viewModel.state.value.profile != null && viewModel.state.value.decidedTieIds.contains("c1")
        }

        composeTestRule.onNodeWithText("Adri").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bio de prueba").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Decisión guardada en empate").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Estadísticas").performClick()
        composeTestRule.onNodeWithText("Mis categorías").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Volver a recuerdos").performClick()
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.onNodeWithText("Editar perfil").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amigos (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacidad").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
    }

    @Test
    fun profileScreen_friendVisitorModeHidesSettingsAndShowsFriendOptions() {
        val ownProfile = UserProfile(uid = "user_1", username = "adri")
        val friendProfile = UserProfile(uid = "f1", username = "ana", bio = "Viajera")
        val viewModel = UiSmokeTestFactories.createProfileViewModel(
            authRepo = FakeUiAuthRepository(User("user_1", "adri@test.com", "adri")),
            profileRepo = FakeUiUserProfileRepository(listOf(ownProfile, friendProfile)),
            capriceRepo = FakeUiCapriceRepository(emptyList()),
            friendshipRepo = FakeUiFriendshipRepository()
        )

        composeTestRule.setContent {
            ProfileScreen(
                viewModel = viewModel,
                targetUserId = "f1",
                currentUserId = "user_1",
                onSignedOut = {}
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            viewModel.state.value.profile?.uid == "f1"
        }

        composeTestRule.onNodeWithText("@ana").assertIsDisplayed()
        composeTestRule.onNodeWithText("Viajera").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volver").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Ajustes").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Opciones").performClick()
        composeTestRule.onNodeWithText("Eliminar amigo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bloquear usuario").assertIsDisplayed()
    }

    @Test
    fun authScreen_switchesToRegisterAndOpensBirthdayDialog() {
        val viewModel = UiSmokeTestFactories.createAuthViewModel()

        composeTestRule.setContent {
            AuthScreen(
                viewModel = viewModel,
                onNavigateToHome = {}
            )
        }

        composeTestRule.onNodeWithText("Entrar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continuar con Google").assertIsDisplayed()

        composeTestRule.onNodeWithText("Registrarse").performClick()
        composeTestRule.onNodeWithText("Nombre de usuario (@usuario)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Crear cuenta").assertIsDisplayed()

        composeTestRule.onNodeWithText("Fecha de nacimiento (dd/mm/aaaa)").performClick()
        composeTestRule.onNodeWithText("FECHA DE NACIMIENTO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Elige tu fecha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aceptar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").performClick()
    }

    @Test
    fun homeFeedComponents_tabBarSwitchesAndBannerDismisses() {
        var selectedTab by mutableStateOf(HomeFeedTab.FRIENDS)
        var allowClicked = false
        var dismissed = false

        composeTestRule.setContent {
            Column {
                HomeFeedTabBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
                FriendsLocationPermissionBanner(
                    isPermanentlyDenied = false,
                    onAllowClick = { allowClicked = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("General").performClick()
        assertEquals(HomeFeedTab.GENERAL, selectedTab)

        composeTestRule.onNodeWithText("Activa ubicación para ver distancias en Amigos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar").performClick()
        assertTrue(allowClicked)

        composeTestRule.onNodeWithContentDescription("Cerrar aviso").performClick()
        assertTrue(dismissed)
    }
}
