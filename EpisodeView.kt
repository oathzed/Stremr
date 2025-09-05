package com.strmr.ai.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.strmr.ai.data.database.EpisodeEntity
import com.strmr.ai.data.database.SeasonEntity
import com.strmr.ai.data.database.TvShowEntity
import com.strmr.ai.ui.theme.StrmrConstants
import com.strmr.ai.utils.DateFormatter
import com.strmr.ai.utils.resolveImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun EpisodeView(
    show: TvShowEntity,
    viewModel: com.strmr.ai.viewmodel.DetailsViewModel,
    onEpisodeClick: (season: Int, episode: Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    initialSeason: Int? = null,
    initialEpisode: Int? = null,
) {
    var seasons by remember { mutableStateOf<List<SeasonEntity>>(emptyList()) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var selectedEpisodeIndex by remember { mutableStateOf(0) }
    var episodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isSeasonSelectorFocused by remember { mutableStateOf(false) }
    var isEpisodeRowFocused by remember { mutableStateOf(true) }

    val episodeListState = rememberLazyListState()
    val seasonListState = rememberLazyListState()
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }

    // Fetch seasons and episodes
    LaunchedEffect(show.tmdbId) {
        try {
            loading = true
            Log.d(
                "EpisodeView",
                " Fetching seasons for show: ${show.title} (tmdbId: ${show.tmdbId})",
            )
            val fetchedSeasons = viewModel.fetchTvShowSeasons(show.tmdbId)
            seasons = fetchedSeasons
            Log.d("EpisodeView", " Fetched ${fetchedSeasons.size} seasons for ${show.title}")
            fetchedSeasons.forEachIndexed { index, season ->
                Log.d(
                    "EpisodeView",
                    "Season $index: Season ${season.seasonNumber} - ${season.name} (${season.episodeCount} episodes)",
                )
            }

            // Set initial season
            if (initialSeason != null) {
                val seasonIndex = fetchedSeasons.indexOfFirst { it.seasonNumber == initialSeason }
                if (seasonIndex >= 0) {
                    selectedSeasonIndex = seasonIndex
                }
            }

            // Fetch episodes for selected season
            if (fetchedSeasons.isNotEmpty()) {
                val selectedSeason = fetchedSeasons[selectedSeasonIndex]
                val fetchedEpisodes =
                    viewModel.fetchTvShowEpisodes(show.tmdbId, selectedSeason.seasonNumber)
                episodes = fetchedEpisodes
                Log.d(
                    "EpisodeView",
                    " Fetched ${fetchedEpisodes.size} episodes for season ${selectedSeason.seasonNumber}",
                )

                // Set initial episode
                if (initialEpisode != null) {
                    val episodeIndex =
                        fetchedEpisodes.indexOfFirst { it.episodeNumber == initialEpisode }
                    if (episodeIndex >= 0) {
                        selectedEpisodeIndex = episodeIndex
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EpisodeView", " Error fetching seasons/episodes for show ${show.title}", e)
        } finally {
            loading = false
        }
    }

    // Fetch episodes when season selection changes
    LaunchedEffect(selectedSeasonIndex) {
        if (seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
            try {
                val selectedSeason = seasons[selectedSeasonIndex]
                Log.d(
                    "EpisodeView",
                    " Fetching episodes for season: ${selectedSeason.seasonNumber}",
                )

                // Use the current coroutine scope for the API call
                val fetchedEpisodes =
                    withContext(Dispatchers.IO) {
                        viewModel.fetchTvShowEpisodes(show.tmdbId, selectedSeason.seasonNumber)
                    }

                // Only update if this coroutine wasn't cancelled
                if (this@LaunchedEffect.isActive) {
                    episodes = fetchedEpisodes
                    selectedEpisodeIndex = 0 // Reset to first episode when changing seasons
                    Log.d(
                        "EpisodeView",
                        " Fetched ${fetchedEpisodes.size} episodes for season ${selectedSeason.seasonNumber}",
                    )
                }
            } catch (e: Exception) {
                if (this@LaunchedEffect.isActive) { // Only log if not cancelled
                    Log.e(
                        "EpisodeView",
                        " Error fetching episodes for season ${seasons[selectedSeasonIndex].seasonNumber}",
                        e,
                    )
                    episodes = emptyList()
                }
            }
        }
    }

    // Auto-scroll to selected episode with fixed selector behavior
    LaunchedEffect(selectedEpisodeIndex) {
        if (episodes.isNotEmpty() && selectedEpisodeIndex in 0 until episodes.size) {
            // Scroll to keep the selected episode aligned with the fixed selector position
            // The scroll amount should move episodes left while selector stays fixed
            episodeListState.animateScrollToItem(selectedEpisodeIndex)
        }
    }

    // Restore smooth animated left alignment for seasons (like episodes)
    LaunchedEffect(selectedSeasonIndex) {
        if (seasons.isNotEmpty() && selectedSeasonIndex in 0 until seasons.size) {
            seasonListState.animateScrollToItem(selectedSeasonIndex)
        }
    }

    /**
     * Focus management: Safely request focus only after FocusRequesters are initialized and composition is complete.
     */
    LaunchedEffect(episodes, isSeasonSelectorFocused, isEpisodeRowFocused) {
        // Add a small delay to ensure composables are fully initialized
        kotlinx.coroutines.delay(100)

        if (episodes.isNotEmpty() && !isSeasonSelectorFocused && isEpisodeRowFocused) {
            try {
                episodeFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("EpisodeView", "Failed to request episode focus: ${e.message}")
            }
        }
    }

    LaunchedEffect(seasons, isSeasonSelectorFocused) {
        // Add a small delay to ensure composables are fully initialized
        kotlinx.coroutines.delay(100)

        if (seasons.isNotEmpty() && isSeasonSelectorFocused) {
            try {
                seasonFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("EpisodeView", "Failed to request season focus: ${e.message}")
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(StrmrConstants.Colors.BACKGROUND_DARK),
    ) {
        // Backdrop
        show.backdropUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .blur(radius = StrmrConstants.Blur.RADIUS_STANDARD),
                contentScale = ContentScale.Crop,
                alpha = StrmrConstants.Colors.Alpha.LIGHT,
            )
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = StrmrConstants.Colors.TEXT_PRIMARY)
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = 0.dp,
                            end = 0.dp,
                            top = 40.dp,
                        ),
            ) {
                // Header with show title and season count
                Column(
                    modifier =
                        Modifier.padding(
                            start = StrmrConstants.Dimensions.Icons.EXTRA_LARGE,
                            bottom = 12.dp,
                        ),
                ) {
                    // Show title or logo
                    val resolvedLogoSource = resolveImageSource(show.logoUrl)
                    if (resolvedLogoSource != null) {
                        AsyncImage(
                            model = resolvedLogoSource,
                            contentDescription = show.title,
                            modifier =
                                Modifier
                                    .height(72.dp)
                                    .padding(bottom = 8.dp),
                        )
                    } else {
                        Text(
                            text = show.title,
                            color = StrmrConstants.Colors.TEXT_PRIMARY,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Text(
                        text = "${seasons.size} Seasons",
                        color = StrmrConstants.Colors.TEXT_SECONDARY,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                }

                // Season selector - horizontal scrolling aligned with episodes (no white border selector, scroll throttling enabled)
                if (seasons.isNotEmpty()) {
                    val selectorStartSeason = StrmrConstants.Dimensions.Icons.EXTRA_LARGE
                    val seasonButtonWidth = 100.dp
                    val seasonButtonWidthWithSpacing = seasonButtonWidth + 12.dp

                    // --- Throttle/limit left/right key speed ---
                    var lastSeasonNavTime by remember { mutableStateOf(0L) }
                    val throttleMs = 80L

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                    ) {
                        LazyRow(
                            state = seasonListState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(seasonFocusRequester)
                                    .focusable()
                                    .onKeyEvent { event ->
                                        val now = System.currentTimeMillis()
                                        when (event.nativeKeyEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    if (now - lastSeasonNavTime > throttleMs) {
                                                        if (selectedSeasonIndex > 0) {
                                                            selectedSeasonIndex--
                                                            lastSeasonNavTime = now
                                                        }
                                                    }
                                                }
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    if (now - lastSeasonNavTime > throttleMs) {
                                                        if (selectedSeasonIndex < seasons.size - 1) {
                                                            selectedSeasonIndex++
                                                            lastSeasonNavTime = now
                                                        }
                                                    }
                                                }
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    isSeasonSelectorFocused = false
                                                    isEpisodeRowFocused = true
                                                }
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_BACK -> {
                                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    onBack()
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                            contentPadding =
                                PaddingValues(
                                    start = selectorStartSeason,
                                    end = selectorStartSeason,
                                ),
                        ) {
                            itemsIndexed(seasons) { index, season ->
                                SeasonButtonNoBorder(
                                    season = season,
                                    isSelected = index == selectedSeasonIndex,
                                    isRowFocused = isSeasonSelectorFocused,
                                    onClick = {
                                        selectedSeasonIndex = index
                                        isSeasonSelectorFocused = true
                                        isEpisodeRowFocused = false
                                    },
                                )
                            }
                            repeat(8) { item { Spacer(modifier = Modifier.width(seasonButtonWidthWithSpacing)) } }
                        }
                        // --- NO border selector overlay here anymore ---
                    }
                }

                // Episodes section
                // Episode count text - inline with description
                if (episodes.isNotEmpty()) {
                    Text(
                        text = "${episodes.size} Episodes",
                        color =
                            if (isEpisodeRowFocused) {
                                StrmrConstants.Colors.TEXT_PRIMARY.copy(alpha = 0.8f)
                            } else {
                                StrmrConstants.Colors.TEXT_SECONDARY
                            },
                        fontSize = 14.sp,
                        modifier =
                            Modifier.padding(
                                start = StrmrConstants.Dimensions.Icons.EXTRA_LARGE,
                                bottom = 16.dp,
                            ),
                    )
                }

                // Episodes row - with selector border overlaid on top and key event throttling
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val selectorStart = StrmrConstants.Dimensions.Icons.EXTRA_LARGE
                    val episodeCardWidth = 200.dp
                    val episodeCardWidthWithSpacing = episodeCardWidth + 12.dp

                    // --- Throttle/limit left/right key speed for episodes ---
                    var lastEpisodeNavTime by remember { mutableStateOf(0L) }
                    val throttleMs = 80L

                    LazyRow(
                        state = episodeListState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(if (isEpisodeRowFocused) episodeFocusRequester else FocusRequester())
                                .focusable()
                                .onKeyEvent { event ->
                                    val now = System.currentTimeMillis()
                                    when (event.nativeKeyEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                if (now - lastEpisodeNavTime > throttleMs) {
                                                    if (selectedEpisodeIndex > 0) {
                                                        selectedEpisodeIndex--
                                                        lastEpisodeNavTime = now
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                if (now - lastEpisodeNavTime > throttleMs) {
                                                    if (selectedEpisodeIndex < episodes.size - 1) {
                                                        selectedEpisodeIndex++
                                                        lastEpisodeNavTime = now
                                                    }
                                                }
                                            }
                                            true
                                        }

                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                isEpisodeRowFocused = false
                                                isSeasonSelectorFocused = true
                                            }
                                            true
                                        }

                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                val selectedSeason = seasons[selectedSeasonIndex]
                                                val selectedEpisode = episodes[selectedEpisodeIndex]
                                                onEpisodeClick(
                                                    selectedSeason.seasonNumber,
                                                    selectedEpisode.episodeNumber,
                                                )
                                            }
                                            true
                                        }

                                        android.view.KeyEvent.KEYCODE_BACK -> {
                                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                onBack()
                                            }
                                            true
                                        }

                                        else -> false
                                    }
                                },
                        contentPadding =
                            PaddingValues(
                                start = selectorStart,
                                end = selectorStart,
                            ),
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            val episodeAlpha =
                                when {
                                    isSeasonSelectorFocused -> 0.5f
                                    isEpisodeRowFocused && index == selectedEpisodeIndex -> 1f
                                    else -> 0.5f
                                }
                            Box(
                                modifier =
                                    Modifier.graphicsLayer {
                                        alpha = episodeAlpha
                                    },
                            ) {
                                EpisodeCardNoFocusBorder(
                                    episode = episode,
                                    onClick = {
                                        selectedEpisodeIndex = index
                                        isEpisodeRowFocused = true
                                        isSeasonSelectorFocused = false
                                    },
                                    isSelected = (index == selectedEpisodeIndex),
                                    isEpisodeRowFocused = isEpisodeRowFocused,
                                )
                            }
                        }
                        // Add three end spacers so last episode scrolls fully left
                        item { Spacer(modifier = Modifier.width(episodeCardWidthWithSpacing)) }
                        item { Spacer(modifier = Modifier.width(episodeCardWidthWithSpacing)) }
                        item { Spacer(modifier = Modifier.width(episodeCardWidthWithSpacing)) }
                    }
                    // Fixed selector overlay: fades if you're not in episode row
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    alpha = if (isSeasonSelectorFocused) 0.5f else 1f
                                }
                                .padding(start = selectorStart)
                                .width(episodeCardWidth)
                                .height(112.dp)
                                .border(
                                    width = 3.dp,
                                    color = StrmrConstants.Colors.TEXT_PRIMARY,
                                    shape = RoundedCornerShape(8.dp),
                                ),
                    )
                }
                LaunchedEffect(selectedEpisodeIndex) {
                    if (episodes.isNotEmpty() && selectedEpisodeIndex in 0 until episodes.size) {
                        episodeListState.animateScrollToItem(selectedEpisodeIndex)
                    }
                }

                // Description area for focused episode only
                Spacer(modifier = Modifier.height(6.dp))
                if (episodes.isNotEmpty() && selectedEpisodeIndex < episodes.size) {
                    val selectedEpisode = episodes[selectedEpisodeIndex]
                    selectedEpisode.overview?.let { overview ->
                        if (overview.isNotBlank()) {
                            Text(
                                text = overview,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .padding(
                                            start = StrmrConstants.Dimensions.Icons.EXTRA_LARGE,
                                            end = StrmrConstants.Dimensions.Icons.EXTRA_LARGE,
                                        )
                                        .graphicsLayer {
                                            alpha = if (isEpisodeRowFocused) 1f else 0.65f
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonButtonNoBorder(
    season: SeasonEntity,
    isSelected: Boolean,
    isRowFocused: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .width(100.dp)
                .height(40.dp)
                .background(
                    color =
                        when {
                            isSelected && isRowFocused -> Color.White
                            isSelected && !isRowFocused -> Color.White.copy(alpha = 0.7f)
                            else -> Color.Gray.copy(alpha = 0.4f)
                        },
                    shape = RoundedCornerShape(8.dp),
                )
                .focusable(interactionSource = interactionSource)
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Season ${season.seasonNumber}",
            color = if (isSelected) StrmrConstants.Colors.BACKGROUND_DARK else StrmrConstants.Colors.TEXT_PRIMARY,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun EpisodeCardNoFocusBorder(
    episode: EpisodeEntity,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isEpisodeRowFocused: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            Modifier
                .width(200.dp)
                .focusable(interactionSource = interactionSource)
                .clickable { onClick() },
    ) {
        // Episode thumbnail
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StrmrConstants.Colors.BACKGROUND_DARK.copy(alpha = 0.6f)),
        ) {
            // Episode still/thumbnail
            if (!episode.stillUrl.isNullOrBlank()) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w500${episode.stillUrl}",
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Play icon overlay - only if focused
            if (isSelected && isEpisodeRowFocused) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                color = StrmrConstants.Colors.BACKGROUND_DARK.copy(alpha = 0.8f),
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Runtime overlay
            episode.runtime?.let { runtime ->
                if (runtime > 0 && isSelected && isEpisodeRowFocused) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    color = StrmrConstants.Colors.BACKGROUND_DARK.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${runtime}m",
                            color = StrmrConstants.Colors.TEXT_PRIMARY,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.height(58.dp),
        ) {
            Column {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name ?: "Episode ${episode.episodeNumber}"}",
                    color = StrmrConstants.Colors.TEXT_PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episode.airDate?.let { airDate ->
                        DateFormatter.formatEpisodeDate(airDate)?.let { formattedDate ->
                            Text(
                                text = formattedDate,
                                color = Color(0xFFFAFAFA),
                                fontSize = 12.sp,
                            )
                        }
                    } ?: Spacer(modifier = Modifier.width(1.dp))

                    episode.rating?.let { rating ->
                        if (rating > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = String.format("%.1f", rating),
                                    color = Color(0xFFFAFAFA),
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "★",
                                    color = Color(0xFFFAFAFA).copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
