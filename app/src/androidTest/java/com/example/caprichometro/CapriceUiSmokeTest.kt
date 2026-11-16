package com.example.caprichometro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.infrastructure.ui.ActivePollScreen
import com.example.caprichometro.infrastructure.ui.CapriceScreen
import com.example.caprichometro.infrastructure.ui.ResultScreen
import com.example.caprichometro.infrastructure.ui.home.FeedItemsList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CapriceUiSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun activePollScreen_rendersQuestionOptionsAndAuthor() {
        val liveCaprice = Caprice(
            id = "cap_1",
            userId = "friend_1",
            type = "Compra",
            question = "¿Me compro los auriculares?",
            answer1 = "Sí, merecen la pena",
            answer2 = "No, ahorra",
            timestamp = System.currentTimeMillis() - 60_000L
        )

        composeTestRule.setContent {
            ActivePollScreen(
                caprice = liveCaprice,
                authorUsername = "adrian",
                currentUserId = "voter_1",
                approximateDistanceMeters = 1_500.0
            )
        }

        composeTestRule.onNodeWithText("¿Me compro los auriculares?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sí, merecen la pena").assertIsDisplayed()
        composeTestRule.onNodeWithText("No, ahorra").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duda de @adrian").assertIsDisplayed()
    }

    @Test
    fun resultScreen_rendersExpiredCapriceQuestion() {
        val expiredCaprice = Caprice(
            id = "cap_2",
            userId = "user_1",
            type = "Plan",
            question = "¿Escapada rural este finde?",
            answer1 = "Claro",
            answer2 = "En casa",
            timestamp = System.currentTimeMillis() - PollTiming.POLL_DURATION_MILLIS - 120_000L
        )

        composeTestRule.setContent {
            ResultScreen(
                caprice = expiredCaprice,
                currentUserId = "user_1",
                authorUsername = "adri",
                onGoHome = {}
            )
        }

        composeTestRule.onNodeWithText("VOTACIÓN CERRADA").assertIsDisplayed()
        composeTestRule.onNodeWithText("@adri").assertIsDisplayed()
        composeTestRule.onNodeWithText("Claro").assertIsDisplayed()
        composeTestRule.onNodeWithText("En casa").assertIsDisplayed()
    }

    @Test
    fun feedItemsList_showsFarSectionHeaderAndLoadsMore() {
        var loadedMore = false
        val now = System.currentTimeMillis()
        val items = listOf(
            CapriceFeedItem(
                caprice = Caprice(
                    id = "c_local",
                    userId = "u1",
                    question = "¿Café cerca?",
                    answer1 = "Sí",
                    answer2 = "No",
                    timestamp = now - 10_000L
                ),
                author = UserProfile(uid = "u1", username = "local"),
                approximateDistanceMeters = 5_000.0
            ),
            CapriceFeedItem(
                caprice = Caprice(
                    id = "c_far",
                    userId = "u2",
                    question = "¿Viaje a Bilbao?",
                    answer1 = "Vamos",
                    answer2 = "Paso",
                    timestamp = now - 20_000L
                ),
                author = UserProfile(uid = "u2", username = "lejano"),
                approximateDistanceMeters = 120_000.0
            )
        )

        composeTestRule.setContent {
            FeedItemsList(
                items = items,
                totalCount = 25,
                hasMore = true,
                isBackgroundSyncing = false,
                onLoadMore = { loadedMore = true },
                nowMillis = now,
                onSelectFeedItem = {},
                onNavigateToUserProfile = {},
                onOpenNonFriendProfile = {},
                onImageClick = {}
            )
        }

        composeTestRule.onNodeWithText("¿Café cerca?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Caprichos más lejanos").assertIsDisplayed()
        composeTestRule.onNodeWithText("> 50 km").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Viaje a Bilbao?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cargar 20 más").performClick()
        assertTrue(loadedMore)
    }

    @Test
    fun capriceScreen_selectsTypeFiltersAudienceAndSubmitsCaprice() {
        val capriceRepo = FakeUiCapriceRepository()
        val viewModel = UiSmokeTestFactories.createCapriceViewModel(capriceRepo = capriceRepo)
        val friends = listOf(
            UserProfile(uid = "f1", username = "ana"),
            UserProfile(uid = "f2", username = "carlos")
        )
        var navigatedToPoll = false

        composeTestRule.setContent {
            CapriceScreen(
                viewModel = viewModel,
                friends = friends,
                onNavigateToActivePoll = { navigatedToPoll = true }
            )
        }

        composeTestRule.onNodeWithText("Antes de lanzarlo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visible para todos tus amigos (2)").performScrollTo().performClick()

        composeTestRule.onNodeWithText("¿A qué amigos enviarlo?").assertIsDisplayed()
        composeTestRule.onNodeWithText("@ana").assertIsDisplayed()
        composeTestRule.onNodeWithText("@carlos").performClick()
        composeTestRule.onNodeWithText("Listo").performClick()

        composeTestRule.onNodeWithText("Visible para 1 de 2 amigos").assertIsDisplayed()

        composeTestRule.onNodeWithText("🎉 Plan").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Ej. ¿Me compro estas zapatillas?")
            .performScrollTo()
            .performTextInput("¿Vamos al concierto del viernes?")
        composeTestRule.onNodeWithText("Ej. Sí, te quedan genial")
            .performScrollTo()
            .performTextInput("De cabeza")
        composeTestRule.onNodeWithText("Ej. No, ahorra un poco")
            .performScrollTo()
            .performTextInput("Mejor en casa")

        composeTestRule.onNodeWithText("Lanzar a mis amigos").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) { navigatedToPoll }
        assertEquals(1, capriceRepo.savedCaprices.size)
        val saved = capriceRepo.savedCaprices.first()
        assertEquals("Plan", saved.type)
        assertEquals("¿Vamos al concierto del viernes?", saved.question)
        assertEquals(listOf("user_1", "f1"), saved.visibleToUserIds)
    }

    @Test
    fun capriceScreen_whenNearbyEnabledPublishesWithNearbyCell() {
        val capriceRepo = FakeUiCapriceRepository()
        val viewModel = UiSmokeTestFactories.createCapriceViewModel(capriceRepo = capriceRepo)
        viewModel.onLocationPermissionChanged(true)
        viewModel.setNearbyDiscoveryEnabled(true)
        var navigatedToPoll = false

        composeTestRule.setContent {
            CapriceScreen(
                viewModel = viewModel,
                friends = emptyList(),
                onNavigateToActivePoll = { navigatedToPoll = true }
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            viewModel.state.value.preparedNearbyCell != null
        }

        composeTestRule.onNodeWithText("✓ Zona aproximada preparada para General")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Ej. ¿Me compro estas zapatillas?")
            .performScrollTo()
            .performTextInput("¿Compro la chaqueta verde?")
        composeTestRule.onNodeWithText("Ej. Sí, te quedan genial")
            .performScrollTo()
            .performTextInput("Cómprala")
        composeTestRule.onNodeWithText("Ej. No, ahorra un poco")
            .performScrollTo()
            .performTextInput("Pasa")

        composeTestRule.onNodeWithText("Publicar en General").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) { navigatedToPoll }
        assertEquals(1, capriceRepo.savedCaprices.size)
        assertNotNull(capriceRepo.savedCaprices.first().nearbyCell)
    }

    @Test
    fun resultScreen_confirmsAndRectifiesFinalDecision() {
        val expiredCaprice = Caprice(
            id = "cap_res_1",
            userId = "user_1",
            type = "Compra",
            question = "¿Me llevo la cafetera?",
            answer1 = "Cogerla ya",
            answer2 = "Esperar",
            timestamp = System.currentTimeMillis() - PollTiming.POLL_DURATION_MILLIS - 120_000L
        )
        val capriceRepo = FakeUiCapriceRepository(listOf(expiredCaprice))
        val voteRepo = FakeUiVoteRepository(
            mapOf("cap_res_1" to listOf(Vote(capriceId = "cap_res_1", voterId = "f1", option = 1, timestamp = 1L)))
        )
        val viewModel = UiSmokeTestFactories.createActivePollViewModel(capriceRepo, voteRepo)

        composeTestRule.setContent {
            ResultScreen(
                caprice = expiredCaprice,
                viewModel = viewModel,
                currentUserId = "user_1",
                authorUsername = "adri",
                onGoHome = {}
            )
        }

        composeTestRule.onNodeWithText("PENDIENTE DE CONFIRMAR TU DECISIÓN")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("A · Cogerla ya").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            capriceRepo.savedCaprices.first().finalDecision == 1
        }
        composeTestRule.onNodeWithText("TU DECISIÓN FINAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Rectificar opinión final").performScrollTo().performClick()
        composeTestRule.onNodeWithText("RECTIFICAR TU DECISIÓN FINAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("B · Esperar").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            capriceRepo.savedCaprices.first().finalDecision == 2
        }
        composeTestRule.onNodeWithText("B · Esperar").performScrollTo().assertIsDisplayed()
    }
}
