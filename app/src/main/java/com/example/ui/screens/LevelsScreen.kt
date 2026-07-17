package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.ui.GamificationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val gameProgress by viewModel.gamificationState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Generate list of levels from 1 to 50
    val levelsList = remember {
        (1..50).map { level ->
            val title = viewModel.getTitleForLevel(level)
            val xpThreshold = viewModel.getXpThresholdForLevel(level)
            val xpNeededForNext = viewModel.getXpThresholdForLevel(level + 1) - xpThreshold
            
            val reward = when (level) {
                1 -> "100 Coins"
                2 -> "150 Coins"
                3 -> "Route Explorer Badge"
                4 -> "250 Coins"
                5 -> "Business Pro Badge"
                10 -> "Snack Champion Badge"
                20 -> "Distribution Master Badge"
                30 -> "Business Legend Badge"
                50 -> "Legendary Snack Empire Badge"
                else -> {
                    if (level % 2 == 0) {
                        "${100 + level * 25} Coins"
                    } else {
                        "${50 + level * 20} Coins"
                    }
                }
            }
            LevelListItem(
                level = level,
                title = title,
                reward = reward,
                xpThreshold = xpThreshold,
                xpNeededForNext = xpNeededForNext
            )
        }
    }

    // Scroll to current level on load
    LaunchedEffect(gameProgress.level) {
        val targetIndex = (gameProgress.level - 2).coerceAtLeast(0)
        listState.animateScrollToItem(targetIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Levels Arena", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("levels_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = Modifier.testTag("levels_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- HERO STATUS CARD ---
            HeroLevelCard(state = gameProgress)

            Spacer(modifier = Modifier.height(12.dp))

            // Section Subheader
            Text(
                text = "Levels Path (50 Levels Available)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("levels_path_header")
            )

            // --- LEVEL PROGRESS PATH LIST ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(levelsList, key = { it.level }) { item ->
                    val isCurrent = item.level == gameProgress.level
                    val isUnlocked = gameProgress.level >= item.level
                    
                    val progress = when {
                        isUnlocked && !isCurrent -> 1f
                        isCurrent -> gameProgress.xpProgress
                        else -> 0f
                    }

                    LevelItemRow(
                        item = item,
                        isCurrent = isCurrent,
                        isUnlocked = isUnlocked,
                        progress = progress,
                        currentXp = gameProgress.xp
                    )
                }
            }
        }
    }
}

@Composable
fun HeroLevelCard(state: GamificationState) {
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("hero_level_card"),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Level Circle Avatar
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LEVEL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = state.level.toString(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Title & Rank Details
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Rank: ${state.rank}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Total XP: ${state.xp}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Cumulative dynamic XP requirement bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Level Progress",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "${(state.xpProgress * 100).toInt()}% (${state.xpNeededForNextLevel} XP needed)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.xpProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = Color(0xFFFFD700),
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun LevelItemRow(
    item: LevelListItem,
    isCurrent: Boolean,
    isUnlocked: Boolean,
    progress: Float,
    currentXp: Int
) {
    val cardBgColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isUnlocked -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }

    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("level_row_${item.level}")
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Level Icon/Circle Indicator
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isUnlocked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "L${item.level}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isUnlocked -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Rewards
                    Column {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "🎁 Reward: ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = item.reward,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.secondary
                                } else if (isUnlocked) {
                                    Color(0xFF2E7D32)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                }

                // Unlock status indicator icon
                Icon(
                    imageVector = when {
                        isCurrent -> Icons.Default.Star
                        isUnlocked -> Icons.Default.CheckCircle
                        else -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = when {
                        isCurrent -> Color(0xFFFFD700)
                        isUnlocked -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Sub-progress bar inside each level item
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isUnlocked -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Required: ${item.xpThreshold} XP",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    if (isCurrent) {
                        Text(
                            text = "${currentXp} / ${item.xpThreshold + item.xpNeededForNext} XP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isUnlocked) {
                        Text(
                            text = "Unlocked",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    } else {
                        val remaining = item.xpThreshold - currentXp
                        Text(
                            text = "Locked (${remaining} XP left)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

data class LevelListItem(
    val level: Int,
    val title: String,
    val reward: String,
    val xpThreshold: Int,
    val xpNeededForNext: Int
)
