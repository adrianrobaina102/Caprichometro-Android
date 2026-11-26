package com.example.caprichometro.infrastructure.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.ui.components.AppIcons
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.infrastructure.ui.home.CardBg
import com.example.caprichometro.infrastructure.ui.home.CardBorder
import com.example.caprichometro.infrastructure.ui.home.FeedActionState
import com.example.caprichometro.infrastructure.ui.home.FeedBg
import com.example.caprichometro.infrastructure.ui.home.FeedErrorState
import com.example.caprichometro.infrastructure.ui.home.FeedItemsList
import com.example.caprichometro.infrastructure.ui.home.FeedLoadingState
import com.example.caprichometro.infrastructure.ui.home.FriendsEmptyState
import com.example.caprichometro.infrastructure.ui.home.FriendsLocationPermissionBanner
import com.example.caprichometro.infrastructure.ui.home.HomeFeedTabBar
import com.example.caprichometro.infrastructure.ui.home.LimeAccent
import com.example.caprichometro.infrastructure.ui.home.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CapriceViewModel,
    onSelectFeedItem: (CapriceFeedItem) -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onOpenNonFriendProfile: (UserProfile) -> Unit = {},
    deferLocationAutoPrompt: Boolean = false,
    ownUserProfile: UserProfile? = null,
    pendingFriendRequestsCount: Int = 0,
    onOwnAvatarClick: () -> Unit = {},
    onOwnAvatarLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var isLocationBannerDismissed by remember { mutableStateOf(false) }
    var isLocationPermanentlyDenied by remember { mutableStateOf(false) }

    val activeOwnCaprice = remember(state.latestCaprices, nowMillis) {
        state.latestCaprices.firstOrNull { !PollTiming.isPollExpired(it.timestamp, nowMillis) }
    }
    var ownPollRemainingMillis by remember(activeOwnCaprice?.id, activeOwnCaprice?.timestamp) {
        mutableLongStateOf(activeOwnCaprice?.let { PollTiming.remainingMillis(it.timestamp) } ?: 0L)
    }
    LaunchedEffect(activeOwnCaprice?.id, activeOwnCaprice?.timestamp) {
        val active = activeOwnCaprice
        if (active == null) {
            ownPollRemainingMillis = 0L
            return@LaunchedEffect
        }
        while (true) {
            val remaining = PollTiming.remainingMillis(active.timestamp)
            ownPollRemainingMillis = remaining
            if (remaining <= 0L) {
                nowMillis = System.currentTimeMillis()
                break
            }
            delay(1_000L)
        }
    }

    val openAppSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    val coarseLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onLocationPermissionChanged(isGranted)
        if (isGranted) {
            isLocationPermanentlyDenied = false
            if (state.selectedHomeTab == HomeFeedTab.GENERAL) {
                nowMillis = System.currentTimeMillis()
                viewModel.loadNearbyFeed()
            }
        } else {
            val activity = context as? Activity
            isLocationPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            if (state.selectedHomeTab == HomeFeedTab.GENERAL) {
                viewModel.onNearbyFeedPermissionDenied()
            }
        }
    }

    // Si el usuario vuelve desde Ajustes tras conceder ubicación, recalculamos Amigos y/o General al instante
    val friendsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val nearbyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val latestNeedsNearbyPermission by rememberUpdatedState(state.nearbyNeedsLocationPermission)
    val latestNeedsNearbyServices by rememberUpdatedState(state.nearbyNeedsLocationServices)
    DisposableEffect(context, state.selectedHomeTab) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val hasPerm = hasCoarseLocationPermission(context)
                    viewModel.onLocationPermissionChanged(hasPerm)
                    if (hasPerm) {
                        isLocationPermanentlyDenied = false
                        if (state.selectedHomeTab == HomeFeedTab.GENERAL &&
                            (latestNeedsNearbyPermission || latestNeedsNearbyServices)
                        ) {
                            nowMillis = System.currentTimeMillis()
                            viewModel.loadNearbyFeedIfNeeded()
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(state.selectedHomeTab, deferLocationAutoPrompt) {
        nowMillis = System.currentTimeMillis()
        val hasPerm = hasCoarseLocationPermission(context)
        viewModel.onLocationPermissionChanged(hasPerm)

        when (state.selectedHomeTab) {
            HomeFeedTab.FRIENDS -> {
                viewModel.loadFriendsFeedIfNeeded()
                if (!hasPerm && !state.hasAutoPromptedLocation && !deferLocationAutoPrompt) {
                    viewModel.markLocationPermissionAutoPrompted()
                    coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            HomeFeedTab.GENERAL -> {
                if (hasPerm) {
                    viewModel.loadNearbyFeedIfNeeded()
                } else if (!state.hasAutoPromptedLocation && !deferLocationAutoPrompt) {
                    viewModel.markLocationPermissionAutoPrompted()
                    viewModel.onNearbyFeedPermissionDenied()
                    coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    viewModel.onNearbyFeedPermissionDenied()
                }
            }
        }
    }

    // Reloj de refresco cada 60s para recalcular "Ahora / Xm / Xh / Xd" y el estado temporal sin consultar Firestore
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val isCurrentTabLoading = if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
        state.isFeedLoading
    } else {
        state.isNearbyFeedLoading
    }
    val isCurrentTabBackgroundSyncing = if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
        state.isFriendsBackgroundSyncing
    } else {
        state.isNearbyBackgroundSyncing
    }
    val hasCurrentTabItems = if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
        state.feedItems.isNotEmpty()
    } else {
        state.nearbyFeedItems.isNotEmpty()
    }
    var isPullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentTabLoading) {
        if (!isCurrentTabLoading) {
            isPullRefreshing = false
        }
    }

    Scaffold(
        containerColor = FeedBg,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (ownUserProfile != null) {
                        OwnHomeAvatarButton(
                            userProfile = ownUserProfile,
                            remainingMillis = ownPollRemainingMillis,
                            onClick = onOwnAvatarClick,
                            onLongClick = onOwnAvatarLongClick
                        )
                    } else {
                        Spacer(modifier = Modifier.size(46.dp))
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    HomeFriendsHeaderButton(
                        pendingRequestsCount = pendingFriendRequestsCount,
                        onClick = onNavigateToFriends
                    )
                }

                HomeFeedTabBar(
                    selectedTab = state.selectedHomeTab,
                    onSelectTab = { tab -> viewModel.selectHomeTab(tab) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )

                if ((isCurrentTabLoading || isCurrentTabBackgroundSyncing) && hasCurrentTabItems) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = LimeAccent,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = {
                nowMillis = System.currentTimeMillis()
                if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
                    isPullRefreshing = true
                    viewModel.loadFriendsFeed(isManualRefresh = true)
                } else if (hasCoarseLocationPermission(context)) {
                    isPullRefreshing = true
                    viewModel.loadNearbyFeed(isManualRefresh = true)
                } else {
                    coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
                when {
                    state.isFeedLoading && state.feedItems.isEmpty() -> {
                        FeedLoadingState(message = stringResource(R.string.home_loading_friends))
                    }

                    state.feedError != null && state.feedItems.isEmpty() -> {
                        FeedErrorState(
                            title = "No se pudo cargar el feed",
                            message = state.feedError ?: "Error desconocido",
                            onRetry = {
                                nowMillis = System.currentTimeMillis()
                                viewModel.loadFriendsFeed(isManualRefresh = true)
                            }
                        )
                    }

                    state.feedItems.isEmpty() -> {
                        FriendsEmptyState(
                            isLoading = state.isFeedLoading,
                            onRefresh = {
                                nowMillis = System.currentTimeMillis()
                                viewModel.loadFriendsFeed(isManualRefresh = true)
                            },
                            onNavigateToFriends = onNavigateToFriends
                        )
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.feedError != null) {
                                Text(
                                    text = state.feedError ?: "",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            if (!state.isLocationPermissionGranted &&
                                state.hasAutoPromptedLocation &&
                                !isLocationBannerDismissed
                            ) {
                                FriendsLocationPermissionBanner(
                                    isPermanentlyDenied = isLocationPermanentlyDenied,
                                    onAllowClick = {
                                        if (isLocationPermanentlyDenied) {
                                            openAppSettings()
                                        } else {
                                            coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                    },
                                    onDismiss = { isLocationBannerDismissed = true }
                                )
                            }
                            FeedItemsList(
                                items = state.feedItems,
                                totalCount = state.totalFriendsFeedCount,
                                hasMore = state.hasMoreFriendsFeed,
                                isBackgroundSyncing = state.isFriendsBackgroundSyncing,
                                onLoadMore = { viewModel.loadMoreFriendsFeed() },
                                nowMillis = nowMillis,
                                closedPollResults = state.closedPollResults,
                                onRequestClosedPollResult = viewModel::loadClosedPollResultIfNeeded,
                                capriceImages = state.capriceImages,
                                onRequestCapriceImage = viewModel::loadCapriceImageIfNeeded,
                                onSelectFeedItem = { selectedItem ->
                                    val resolved = if (selectedItem.caprice.imageUrl.isBlank()) {
                                        state.capriceImages[selectedItem.caprice.id]?.let {
                                            selectedItem.copy(caprice = selectedItem.caprice.copy(imageUrl = it))
                                        } ?: selectedItem
                                    } else {
                                        selectedItem
                                    }
                                    onSelectFeedItem(resolved)
                                },
                                onNavigateToUserProfile = onNavigateToUserProfile,
                                onOpenNonFriendProfile = onOpenNonFriendProfile,
                                onImageClick = { fullScreenImageUrl = it },
                                listState = friendsListState
                            )
                        }
                    }
                }
            } else {
                when {
                    state.isNearbyFeedLoading && state.nearbyFeedItems.isEmpty() -> {
                        FeedLoadingState(message = stringResource(R.string.home_general_loading))
                    }

                    state.nearbyNeedsLocationPermission && state.nearbyFeedItems.isEmpty() -> {
                        FeedActionState(
                            emoji = "📍",
                            title = stringResource(R.string.home_general_perm_title),
                            message = stringResource(R.string.home_general_perm_body),
                            primaryLabel = stringResource(R.string.home_general_perm_allow),
                            onPrimaryClick = {
                                coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            secondaryLabel = stringResource(R.string.action_open_app_settings),
                            onSecondaryClick = openAppSettings
                        )
                    }

                    state.nearbyNeedsLocationServices && state.nearbyFeedItems.isEmpty() -> {
                        FeedActionState(
                            emoji = "🧭",
                            title = stringResource(R.string.home_general_gps_title),
                            message = state.nearbyFeedError ?: stringResource(R.string.home_general_gps_body),
                            primaryLabel = stringResource(R.string.action_retry),
                            onPrimaryClick = {
                                nowMillis = System.currentTimeMillis()
                                viewModel.loadNearbyFeed(isManualRefresh = true)
                            },
                            secondaryLabel = stringResource(R.string.home_general_gps_settings),
                            onSecondaryClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        )
                    }

                    state.nearbyFeedError != null && state.nearbyFeedItems.isEmpty() -> {
                        FeedErrorState(
                            title = stringResource(R.string.home_general_error_title),
                            message = state.nearbyFeedError ?: stringResource(R.string.home_unknown_error),
                            onRetry = {
                                nowMillis = System.currentTimeMillis()
                                if (hasCoarseLocationPermission(context)) {
                                    viewModel.loadNearbyFeed(isManualRefresh = true)
                                } else {
                                    coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            }
                        )
                    }

                    state.nearbyFeedItems.isEmpty() -> {
                        FeedActionState(
                            emoji = "🌍",
                            title = stringResource(R.string.home_general_empty_title),
                            message = stringResource(R.string.home_general_empty_body),
                            primaryLabel = stringResource(R.string.action_refresh),
                            primaryEnabled = !state.isNearbyFeedLoading,
                            onPrimaryClick = {
                                nowMillis = System.currentTimeMillis()
                                viewModel.loadNearbyFeed(isManualRefresh = true)
                            }
                        )
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.nearbyFeedError != null) {
                                Text(
                                    text = state.nearbyFeedError ?: "",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            FeedItemsList(
                                items = state.nearbyFeedItems,
                                totalCount = state.totalNearbyFeedCount,
                                hasMore = state.hasMoreNearbyFeed,
                                isBackgroundSyncing = state.isNearbyBackgroundSyncing,
                                onLoadMore = { viewModel.loadMoreNearbyFeed() },
                                nowMillis = nowMillis,
                                closedPollResults = state.closedPollResults,
                                onRequestClosedPollResult = viewModel::loadClosedPollResultIfNeeded,
                                capriceImages = state.capriceImages,
                                onRequestCapriceImage = viewModel::loadCapriceImageIfNeeded,
                                onSelectFeedItem = { selectedItem ->
                                    val resolved = if (selectedItem.caprice.imageUrl.isBlank()) {
                                        state.capriceImages[selectedItem.caprice.id]?.let {
                                            selectedItem.copy(caprice = selectedItem.caprice.copy(imageUrl = it))
                                        } ?: selectedItem
                                    } else {
                                        selectedItem
                                    }
                                    onSelectFeedItem(resolved)
                                },
                                onNavigateToUserProfile = onNavigateToUserProfile,
                                onOpenNonFriendProfile = onOpenNonFriendProfile,
                                onImageClick = { fullScreenImageUrl = it },
                                listState = nearbyListState
                            )
                        }
                    }
                }
            }
        }
    }

    fullScreenImageUrl?.let { url ->
        FullScreenImageViewer(
            imageUrl = url,
            contentDescription = "Foto de la duda",
            onDismiss = { fullScreenImageUrl = null }
        )
    }
}

private fun hasCoarseLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OwnHomeAvatarButton(
    userProfile: UserProfile,
    remainingMillis: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isActive = remainingMillis > 0L
    val progress = (remainingMillis.toFloat() / PollTiming.POLL_DURATION_MILLIS.toFloat()).coerceIn(0f, 1f)
    val cdText = stringResource(
        if (isActive) R.string.home_own_avatar_active_cd else R.string.home_own_avatar_profile_cd
    )

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = cdText },
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.5.dp.toPx()
                drawCircle(
                    color = CardBorder,
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = LimeAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, CardBorder, CircleShape)
            )
        }
        UserAvatar(
            photoUrl = userProfile.photoUrl,
            username = userProfile.username,
            size = 38.dp,
            backgroundColor = LimeAccent,
            fontSize = 15.sp
        )
    }
}

@Composable
internal fun HomeFriendsHeaderButton(
    pendingRequestsCount: Int,
    onClick: () -> Unit
) {
    val cdText = stringResource(R.string.home_friends_button_cd)
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(1.dp, CardBorder, CircleShape)
        ) {
            Icon(
                imageVector = AppIcons.Group,
                contentDescription = cdText,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        if (pendingRequestsCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pendingRequestsCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
