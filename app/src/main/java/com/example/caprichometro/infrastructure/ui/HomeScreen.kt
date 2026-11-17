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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
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

@Composable
fun HomeScreen(
    viewModel: CapriceViewModel,
    onSelectFeedItem: (CapriceFeedItem) -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onOpenNonFriendProfile: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var isLocationBannerDismissed by remember { mutableStateOf(false) }
    var isLocationPermanentlyDenied by remember { mutableStateOf(false) }

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
                        if (state.selectedHomeTab == HomeFeedTab.GENERAL && state.nearbyNeedsLocationPermission) {
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

    LaunchedEffect(state.selectedHomeTab) {
        nowMillis = System.currentTimeMillis()
        val hasPerm = hasCoarseLocationPermission(context)
        viewModel.onLocationPermissionChanged(hasPerm)

        when (state.selectedHomeTab) {
            HomeFeedTab.FRIENDS -> {
                viewModel.loadFriendsFeedIfNeeded()
                if (!hasPerm && !state.hasAutoPromptedLocation) {
                    viewModel.markLocationPermissionAutoPrompted()
                    coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            HomeFeedTab.GENERAL -> {
                if (hasPerm) {
                    viewModel.loadNearbyFeedIfNeeded()
                } else if (!state.hasAutoPromptedLocation) {
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

    Scaffold(
        containerColor = FeedBg,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.nav_home),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(
                                if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
                                    R.string.home_subtitle_friends
                                } else {
                                    R.string.home_subtitle_general
                                }
                            ),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            nowMillis = System.currentTimeMillis()
                            if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
                                viewModel.loadFriendsFeed(isManualRefresh = true)
                            } else if (hasCoarseLocationPermission(context)) {
                                viewModel.loadNearbyFeed(isManualRefresh = true)
                            } else {
                                coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        enabled = !isCurrentTabLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                            .border(1.dp, CardBorder, CircleShape)
                    ) {
                        if (isCurrentTabLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LimeAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar feed",
                                tint = LimeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
                when {
                    state.isFeedLoading && state.feedItems.isEmpty() -> {
                        FeedLoadingState(message = "Cargando dudas de tus amigos...")
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
                                onSelectFeedItem = onSelectFeedItem,
                                onNavigateToUserProfile = onNavigateToUserProfile,
                                onOpenNonFriendProfile = onOpenNonFriendProfile,
                                onImageClick = { fullScreenImageUrl = it }
                            )
                        }
                    }
                }
            } else {
                when {
                    state.isNearbyFeedLoading && state.nearbyFeedItems.isEmpty() -> {
                        FeedLoadingState(message = "Buscando dudas cercanas en General...")
                    }

                    state.nearbyNeedsLocationPermission && state.nearbyFeedItems.isEmpty() -> {
                        FeedActionState(
                            emoji = "📍",
                            title = "Ubicación aproximada necesaria",
                            message = "Para ordenar las dudas públicas por cercanía necesitamos permiso de ubicación aproximada.",
                            primaryLabel = "Permitir ubicación",
                            onPrimaryClick = {
                                coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            secondaryLabel = "Abrir Ajustes",
                            onSecondaryClick = openAppSettings
                        )
                    }

                    state.nearbyNeedsLocationServices && state.nearbyFeedItems.isEmpty() -> {
                        FeedActionState(
                            emoji = "🧭",
                            title = "Ubicación del dispositivo desactivada",
                            message = state.nearbyFeedError ?: "Activa los servicios de ubicación del sistema para cargar General.",
                            primaryLabel = "Reintentar",
                            onPrimaryClick = {
                                nowMillis = System.currentTimeMillis()
                                viewModel.loadNearbyFeed(isManualRefresh = true)
                            },
                            secondaryLabel = "Ajustes de ubicación",
                            onSecondaryClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        )
                    }

                    state.nearbyFeedError != null && state.nearbyFeedItems.isEmpty() -> {
                        FeedErrorState(
                            title = "No se pudo cargar General",
                            message = state.nearbyFeedError ?: "Error desconocido",
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
                            title = "Nada en General todavía",
                            message = "Cuando otros usuarios publiquen dudas con General activado, aparecerán aquí ordenadas por cercanía.",
                            primaryLabel = "Actualizar",
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
                                onSelectFeedItem = onSelectFeedItem,
                                onNavigateToUserProfile = onNavigateToUserProfile,
                                onOpenNonFriendProfile = onOpenNonFriendProfile,
                                onImageClick = { fullScreenImageUrl = it }
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
