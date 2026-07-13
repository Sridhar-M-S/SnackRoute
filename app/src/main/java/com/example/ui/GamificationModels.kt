package com.example.ui

import androidx.compose.ui.graphics.vector.ImageVector

data class GamificationState(
    val level: Int = 1,
    val xp: Int = 0,
    val xpNeededForNextLevel: Int = 500,
    val xpProgress: Float = 0f,
    val coins: Int = 0,
    val rank: String = "Bronze Seller",
    val streak: Int = 0,
    val title: String = "Beginner Seller",
    val totalSalesCount: Int = 0,
    val totalShopsCount: Int = 0,
    val totalLocationsCount: Int = 0,
    val unlockedBadgesCount: Int = 0,
    val sessionCombo: Int = 0,
    val dailyMissions: List<Mission> = emptyList(),
    val weeklyMissions: List<Mission> = emptyList(),
    val monthlyMissions: List<Mission> = emptyList(),
    val bossChallenges: List<BossChallenge> = emptyList()
)

data class Mission(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val xpReward: Int,
    val coinReward: Int,
    val isCompleted: Boolean,
    val type: String // "daily", "weekly", "monthly"
)

data class BossChallenge(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val xpReward: Int,
    val coinReward: Int,
    val isCompleted: Boolean,
    val bossName: String
)

sealed class GamificationEvent {
    data class XpGain(val amount: Int, val reason: String) : GamificationEvent()
    data class CoinGain(val amount: Int, val reason: String) : GamificationEvent()
    data class LevelUp(val level: Int, val title: String) : GamificationEvent()
    data class MissionComplete(val title: String) : GamificationEvent()
    data class AchievementUnlocked(val name: String) : GamificationEvent()
    data class BossDefeated(val bossName: String) : GamificationEvent()
    data class ComboUpdate(val count: Int, val bonusXp: Int) : GamificationEvent()
}
