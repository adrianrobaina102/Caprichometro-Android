package com.example.caprichometro

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.infrastructure.ui.voting.ActivePollScreen
import com.example.caprichometro.infrastructure.ui.caprice.CapriceScreen
import com.example.caprichometro.infrastructure.ui.home.HomeFriendsHeaderButton
import com.example.caprichometro.infrastructure.ui.home.OwnHomeAvatarButton
import com.example.caprichometro.infrastructure.ui.voting.ResultScreen
import com.example.caprichometro.infrastructure.ui.home.FeedItemsList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CapriceUiSmokeTest {

    @get:Rule
    val locationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_COARSE_LOCATION)

    @get:Rule
    val composeTestRule = createComposeRule()

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

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
        composeTestRule.onNodeWithText(targetContext.getString(R.string.poll_friend_title, "adrian")).assertIsDisplayed()
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

        composeTestRule.onNodeWithText(targetContext.getString(R.string.result_badge_closed)).assertIsDisplayed()
        composeTestRule.onNodeWithText("@adri").assertIsDisplayed()
        composeTestRule.onNodeWithText("Claro").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("En casa").performScrollTo().assertIsDisplayed()
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
        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_far_section_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_far_section_badge)).assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Viaje a Bilbao?").assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.resources.getQuantityString(R.plurals.home_pagination_load_more, 20, 20)).performClick()
        assertTrue(loadedMore)
    }

    @Test
    fun capriceScreen_selectsTypeFiltersAudienceAndSubmitsCaprice() {
        val capriceRepo = FakeUiCapriceRepository()
        val viewModel = UiSmokeTestFactories.createCreateCapriceViewModel(capriceRepo = capriceRepo)
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

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_screen_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_audience_all_friends, 2))
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_audience_sheet_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("@ana").assertIsDisplayed()
        composeTestRule.onNodeWithText("@carlos").performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_done)).performClick()

        composeTestRule.onNodeWithText(targetContext.resources.getQuantityString(R.plurals.create_audience_some_friends, 2, 1, 2))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_type_plan_chip))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_question_placeholder_plan))
            .performScrollTo()
            .performTextInput("¿Vamos al concierto del viernes?")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_default_plan_a))
            .performScrollTo()
            .performTextReplacement("De cabeza")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_default_plan_b))
            .performScrollTo()
            .performTextReplacement("Mejor en casa")

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_submit_friends)).performClick()

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
        val viewModel = UiSmokeTestFactories.createCreateCapriceViewModel(capriceRepo = capriceRepo)
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

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_general_zone_ready))
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_question_placeholder))
            .performScrollTo()
            .performTextInput("¿Compro la chaqueta verde?")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_default_compra_a))
            .performScrollTo()
            .performTextReplacement("Cómprala")
        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_default_compra_b))
            .performScrollTo()
            .performTextReplacement("Pasa")

        composeTestRule.onNodeWithText(targetContext.getString(R.string.create_submit_general)).performClick()

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

        var navigatedHome = false
        composeTestRule.setContent {
            ResultScreen(
                caprice = expiredCaprice,
                viewModel = viewModel,
                currentUserId = "user_1",
                authorUsername = "adri",
                onGoHome = { navigatedHome = true }
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            viewModel.state.value.votes.isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetContext.getString(R.string.result_decision_pending_author_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("A · Cogerla ya").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertFalse("Selecting A must not tap the fixed home button", navigatedHome)
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            capriceRepo.savedCaprices.first().finalDecision == 1
        }
        composeTestRule.onNodeWithText(targetContext.getString(R.string.result_decision_your_final))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.result_decision_rectify_button))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.result_decision_rectify_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("B · Esperar").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertFalse("Selecting B must not tap the fixed home button", navigatedHome)
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            capriceRepo.savedCaprices.first().finalDecision == 2
        }
        composeTestRule.onNodeWithText("B · Esperar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun feedItemsList_opensSampleCommentsPreviewSheet() {
        val now = System.currentTimeMillis()
        val items = listOf(
            CapriceFeedItem(
                caprice = Caprice(
                    id = "c_comments",
                    userId = "u1",
                    type = "Compra",
                    question = "¿Me pillo las zapatillas nuevas?",
                    answer1 = "Sí",
                    answer2 = "No",
                    timestamp = now - 10_000L
                ),
                author = UserProfile(uid = "u1", username = "marta")
            )
        )

        composeTestRule.setContent {
            FeedItemsList(
                items = items,
                totalCount = 1,
                hasMore = false,
                isBackgroundSyncing = false,
                onLoadMore = {},
                nowMillis = now,
                onSelectFeedItem = {},
                onNavigateToUserProfile = {},
                onOpenNonFriendProfile = {},
                onImageClick = {}
            )
        }

        composeTestRule.onNodeWithText(targetContext.getString(R.string.feed_comments_preview_count, 2))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.feed_comments_sheet_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.feed_comments_preview_badge)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.feed_comments_input_placeholder))
            .performTextInput("Totalmente sí")
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.feed_comments_send_cd))
            .performClick()
        composeTestRule.onNodeWithText("Totalmente sí").assertIsDisplayed()
    }

    @Test
    fun ownHomeAvatarButton_rendersActiveAndProfileStates() {
        var clickedActive = false
        var clickedProfile = false

        composeTestRule.setContent {
            Column {
                OwnHomeAvatarButton(
                    userProfile = UserProfile(uid = "u1", username = "adri"),
                    remainingMillis = 120_000L,
                    onClick = { clickedActive = true }
                )
                OwnHomeAvatarButton(
                    userProfile = UserProfile(uid = "u1", username = "adri"),
                    remainingMillis = 0L,
                    onClick = { clickedProfile = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.home_own_avatar_active_cd))
            .performClick()
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.home_own_avatar_profile_cd))
            .performClick()
        composeTestRule.runOnIdle {
            assertTrue(clickedActive)
            assertTrue(clickedProfile)
        }
    }

    @Test
    fun homeFriendsHeaderButton_showsPendingBadgeAndNavigates() {
        var clickedFriends = false
        composeTestRule.setContent {
            HomeFriendsHeaderButton(
                pendingRequestsCount = 3,
                onClick = { clickedFriends = true }
            )
        }

        composeTestRule.onNodeWithText("3").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.home_friends_button_cd))
            .performClick()
        composeTestRule.runOnIdle {
            assertTrue(clickedFriends)
        }
    }
}
