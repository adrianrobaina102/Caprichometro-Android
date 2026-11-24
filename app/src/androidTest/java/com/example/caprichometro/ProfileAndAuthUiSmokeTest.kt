package com.example.caprichometro

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
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

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

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

        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_badge_pending)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.profile_badge_tie_cd)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_memories_footer)).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_stats_listened_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_stats_listened_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.resources.getQuantityString(R.plurals.profile_stats_listened_detail, 4, 3, 4)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_stats_categories_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.category_compras)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.category_planes)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.category_ropa)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.category_otros)).assertIsDisplayed()
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

        val planesChip = targetContext.getString(
            R.string.profile_categories_count_chip,
            targetContext.getString(R.string.category_planes),
            1
        )
        val otrosChip = targetContext.getString(
            R.string.profile_categories_count_chip,
            targetContext.getString(R.string.category_otros),
            0
        )

        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_stats_see_all)).performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_categories_sheet_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_categories_all, 3)).assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Compro la mochila?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Vamos a la playa?").assertIsDisplayed()

        composeTestRule.onNodeWithText(planesChip).performScrollTo().performClick()
        composeTestRule.onNodeWithText("¿Vamos a la playa?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Compro la mochila?").assertDoesNotExist()

        composeTestRule.onNodeWithText(otrosChip).performScrollTo().performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_categories_empty)).assertIsDisplayed()

        composeTestRule.onNodeWithText(planesChip).performScrollTo().performClick()
        composeTestRule.onNodeWithText("¿Vamos a la playa?").performClick()
        assertEquals("c2", selected?.id)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_categories_sheet_title)).assertDoesNotExist()
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

        composeTestRule.onNodeWithText(targetContext.getString(R.string.category_ropa)).performClick()
        composeTestRule.onNodeWithText("¿Me llevo la chaqueta?").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Compro la mochila?").assertDoesNotExist()
    }

    @Test
    fun profileScreen_ownProfileSwitchesToStatsAndOpensSettingsAndLanguagePicker() {
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
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.profile_badge_tie_cd)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.profile_tab_stats_cd)).performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_stats_categories_title)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.profile_back_to_memories_cd)).performClick()
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.profile_settings_cd)).performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_settings_edit_profile)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_settings_friends_count, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_settings_privacy)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_settings_sign_out)).assertIsDisplayed()

        val languageRowText = targetContext.getString(
            R.string.profile_settings_language_with_current,
            targetContext.getString(R.string.language_system)
        )
        composeTestRule.onNodeWithText(languageRowText).performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.language_sheet_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.language_spanish)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.language_english)).assertIsDisplayed()
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
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.action_back)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.profile_settings_cd)).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.action_options)).performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_remove_friend_action)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.profile_block_user_action)).assertIsDisplayed()
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

        composeTestRule.onAllNodesWithText(targetContext.getString(R.string.auth_button_login)).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.auth_google_button)).assertIsDisplayed()

        composeTestRule.onAllNodesWithText(targetContext.getString(R.string.auth_tab_register)).onFirst().performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.auth_username_placeholder)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(targetContext.getString(R.string.auth_button_register)).onFirst().assertIsDisplayed()

        composeTestRule.onNodeWithText(targetContext.getString(R.string.auth_birthday_placeholder)).performClick()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.auth_birthday_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.auth_birthday_headline_empty)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_accept)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.action_cancel)).performClick()
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

        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_tab_general)).performClick()
        assertEquals(HomeFeedTab.GENERAL, selectedTab)

        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_location_banner_text)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_location_banner_activate)).performClick()
        assertTrue(allowClicked)

        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.home_location_banner_dismiss_cd)).performClick()
        assertTrue(dismissed)
    }
}
