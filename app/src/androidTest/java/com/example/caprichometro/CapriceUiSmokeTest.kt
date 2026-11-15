package com.example.caprichometro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.ui.ActivePollScreen
import com.example.caprichometro.infrastructure.ui.ResultScreen
import com.example.caprichometro.infrastructure.ui.home.FeedItemsList
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
        composeTestRule.onNodeWithText("@adrian").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("¿Escapada rural este finde?").assertIsDisplayed()
        composeTestRule.onNodeWithText("@adri").assertIsDisplayed()
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
}
