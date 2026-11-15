package com.example.caprichometro.infrastructure.ui

import android.Manifest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.splitByDistanceRadius
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import kotlinx.coroutines.delay

private val FeedBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF18181A)
private val CardBorder = Color(0xFF28282C)
private val OptionBg = Color(0xFF222225)
private val LimeAccent = Color(0xFFD4FF00)
private val TextSecondary = Color(0xFF9E9EA4)
private val ClosedBadgeBg = Color(0xFF2A2A2E)

@Composable
fun HomeScreen(
    viewModel: CapriceViewModel,
    onSelectFeedItem: (CapriceFeedItem) -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var hasAutoPromptedNearby by remember { mutableStateOf(false) }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            nowMillis = System.currentTimeMillis()
            viewModel.loadNearbyFeed()
        } else {
            viewModel.onNearbyFeedPermissionDenied()
        }
    }

    // Si el usuario vuelve desde Ajustes tras conceder ubicación en General, recargamos al instante
    DisposableEffect(context, state.selectedHomeTab) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME &&
                    state.selectedHomeTab == HomeFeedTab.GENERAL &&
                    state.nearbyNeedsLocationPermission &&
                    hasCoarseLocationPermission(context)
                ) {
                    nowMillis = System.currentTimeMillis()
                    viewModel.loadNearbyFeedIfNeeded()
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
        when (state.selectedHomeTab) {
            HomeFeedTab.FRIENDS -> viewModel.loadFriendsFeedIfNeeded()
            HomeFeedTab.GENERAL -> {
                if (hasCoarseLocationPermission(context)) {
                    viewModel.loadNearbyFeedIfNeeded()
                } else if (!hasAutoPromptedNearby) {
                    hasAutoPromptedNearby = true
                    viewModel.onNearbyFeedPermissionDenied()
                    nearbyPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                            text = "Inicio",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (state.selectedHomeTab == HomeFeedTab.FRIENDS) {
                                "Las dudas recientes de tus amigos"
                            } else {
                                "Dudas públicas ordenadas por cercanía"
                            },
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
                                nearbyPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                        FeedItemsList(
                            items = state.feedItems,
                            totalCount = state.totalFriendsFeedCount,
                            hasMore = state.hasMoreFriendsFeed,
                            isBackgroundSyncing = state.isFriendsBackgroundSyncing,
                            onLoadMore = { viewModel.loadMoreFriendsFeed() },
                            nowMillis = nowMillis,
                            onSelectFeedItem = onSelectFeedItem,
                            onNavigateToUserProfile = onNavigateToUserProfile,
                            onImageClick = { fullScreenImageUrl = it }
                        )
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
                                nearbyPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            secondaryLabel = "Abrir Ajustes",
                            onSecondaryClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            }
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
                                    nearbyPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                                onSelectFeedItem = onSelectFeedItem,
                                onNavigateToUserProfile = onNavigateToUserProfile,
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

@Composable
private fun HomeFeedTabBar(
    selectedTab: HomeFeedTab,
    onSelectTab: (HomeFeedTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HomeFeedTabPill(
            label = "Amigos",
            selected = selectedTab == HomeFeedTab.FRIENDS,
            onClick = { onSelectTab(HomeFeedTab.FRIENDS) },
            modifier = Modifier.weight(1f)
        )
        HomeFeedTabPill(
            label = "General",
            selected = selectedTab == HomeFeedTab.GENERAL,
            onClick = { onSelectTab(HomeFeedTab.GENERAL) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeFeedTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LimeAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun FeedLoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = LimeAccent)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun FeedErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = LimeAccent,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Reintentar", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FriendsEmptyState(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateToFriends: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(1.dp, CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👀", fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Nada por aquí todavía",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cuando tus amigos publiquen una compra o un plan, aparecerán aquí las 20 dudas más recientes.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Actualizar", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNavigateToFriends,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Buscar amigos", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeedActionState(
    emoji: String,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(1.dp, CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (secondaryLabel != null && onSecondaryClick != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(secondaryLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(primaryLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeedItemsList(
    items: List<CapriceFeedItem>,
    totalCount: Int,
    hasMore: Boolean,
    isBackgroundSyncing: Boolean,
    onLoadMore: () -> Unit,
    nowMillis: Long,
    onSelectFeedItem: (CapriceFeedItem) -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onImageClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val sections = remember(items) { splitByDistanceRadius(items) }
    val showPaginationFooter = (hasMore || totalCount > CapriceViewModel.FEED_PAGE_SIZE) &&
        (!isBackgroundSyncing || !hasMore || (items.size >= CapriceViewModel.FEED_PAGE_SIZE && items.size % CapriceViewModel.FEED_PAGE_SIZE == 0))
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sección < 50 km sin cabecera ni texto adicional
        items(
            items = sections.localItems,
            key = { "${it.author.uid}_${it.caprice.id}_${it.caprice.timestamp}" }
        ) { item ->
            CapriceFeedCard(
                item = item,
                nowMillis = nowMillis,
                onClick = { onSelectFeedItem(item) },
                onNavigateToUserProfile = onNavigateToUserProfile,
                onImageClick = onImageClick
            )
        }

        if (sections.farItems.isNotEmpty()) {
            item(key = "far_caprices_section_header") {
                FarCapricesSectionHeader()
            }
            items(
                items = sections.farItems,
                key = { "far_${it.author.uid}_${it.caprice.id}_${it.caprice.timestamp}" }
            ) { item ->
                CapriceFeedCard(
                    item = item,
                    nowMillis = nowMillis,
                    onClick = { onSelectFeedItem(item) },
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onImageClick = onImageClick
                )
            }
        }

        if (showPaginationFooter) {
            item(key = "feed_pagination_footer") {
                FeedPaginationFooter(
                    visibleCount = items.size,
                    totalCount = totalCount,
                    hasMore = hasMore,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun FarCapricesSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = CardBorder
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CardBg)
                .border(1.dp, CardBorder, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Caprichos más lejanos",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "> 50 km",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = CardBorder
        )
    }
}

@Composable
private fun FeedPaginationFooter(
    visibleCount: Int,
    totalCount: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    val remaining = (totalCount - visibleCount).coerceAtLeast(0)
    val nextBatch = remaining.coerceAtMost(CapriceViewModel.FEED_PAGE_SIZE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasMore) {
                "Mostrando $visibleCount de $totalCount caprichos"
            } else {
                "Mostrando los $visibleCount caprichos disponibles"
            },
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        if (hasMore) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent.copy(alpha = 0.14f),
                    contentColor = LimeAccent
                ),
                border = BorderStroke(1.dp, LimeAccent)
            ) {
                Text(
                    text = "Cargar $nextBatch más",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CapriceFeedCard(
    item: CapriceFeedItem,
    nowMillis: Long,
    onClick: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    val caprice = item.caprice
    val author = item.author
    val isActive = (nowMillis - caprice.timestamp) < PollNotificationScheduler.POLL_DURATION_MILLIS
    val relativeTime = remember(caprice.timestamp, nowMillis) {
        formatRelativeTime(caprice.timestamp, nowMillis)
    }
    val statusText = remember(caprice.timestamp, nowMillis) {
        formatPollStatus(caprice.timestamp, nowMillis)
    }
    val imageModel = remember(caprice.imageUrl) {
        ImageModelHelper.parseImageModel(caprice.imageUrl)
    }
    val typeEmoji = if (caprice.type.contains("compra", ignoreCase = true)) "🛍️" else "🎉"
    val initial = author.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Cabecera: Avatar + @username clicables solo si es un perfil accesible (no desconocidos de General)
        val canOpenAuthor = item.canOpenAuthorProfile && author.uid.isNotBlank()
        val distanceLabel = remember(item.approximateDistanceMeters) {
            item.approximateDistanceMeters?.let { NearbyCellGrid.formatApproximateDistanceMeters(it) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (canOpenAuthor) {
                            Modifier.clickable { onNavigateToUserProfile(author.uid) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    photoUrl = author.photoUrl,
                    username = author.username,
                    size = 38.dp,
                    backgroundColor = LimeAccent,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "@${author.username}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$typeEmoji ${caprice.type} • $relativeTime",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (distanceLabel != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = LimeAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = distanceLabel,
                                color = LimeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) LimeAccent.copy(alpha = 0.16f) else ClosedBadgeBg)
                    .border(
                        width = 1.dp,
                        color = if (isActive) LimeAccent else CardBorder,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    color = if (isActive) LimeAccent else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pregunta principal
        Text(
            text = caprice.question,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp
        )

        // Foto opcional debajo de la pregunta y antes de las opciones
        if (caprice.imageUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121214))
                    .clickable { onImageClick(caprice.imageUrl) }
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = caprice.question,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ampliar foto",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dos opciones informativas
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedOptionPill(label = "A", text = caprice.answer1)
            FeedOptionPill(label = "B", text = caprice.answer2)
        }
    }
}

@Composable
private fun FeedOptionPill(label: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OptionBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(CardBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = LimeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatRelativeTime(timestampMillis: Long, nowMillis: Long): String {
    val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1L -> "Ahora"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        else -> "${days}d"
    }
}

internal fun formatPollStatus(timestampMillis: Long, nowMillis: Long): String {
    val totalDuration = PollNotificationScheduler.POLL_DURATION_MILLIS
    val closeTimestamp = timestampMillis + totalDuration
    val remainingMillis = closeTimestamp - nowMillis

    return if (remainingMillis > 0) {
        val remainingMin = kotlin.math.ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
        if (remainingMin == 1L) "Queda 1 min" else "Quedan $remainingMin min"
    } else {
        val closedSinceMillis = (nowMillis - closeTimestamp).coerceAtLeast(0L)
        val closedMinutes = closedSinceMillis / 60_000L
        val closedHours = closedMinutes / 60L
        val closedDays = closedHours / 24L

        when {
            closedMinutes < 1L -> "Cerró ahora"
            closedMinutes < 60L -> "Cerró hace ${closedMinutes} min"
            closedHours < 24L -> "Cerró hace ${closedHours}h"
            else -> "Cerró hace ${closedDays}d"
        }
    }
}
