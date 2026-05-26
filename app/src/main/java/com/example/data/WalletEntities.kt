package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_wallet")
data class UserWallet(
    @PrimaryKey val id: Int = 1,
    val userName: String = "Maharana Ludo",
    val depositBalance: Double = 500.0,
    val winningBalance: Double = 0.0,
    val bonusBalance: Double = 50.0,
    val inviteCode: String = "ROYAL777",
    val referralCount: Int = 0,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val totalEarnings: Double = 0.0,
    val isBanned: Boolean = false
) {
    val totalBalance: Double get() = depositBalance + winningBalance + bonusBalance
}

@Entity(tableName = "ludo_table")
data class LudoTable(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entryAmount: Double,
    val playerCount: Int, // 2 or 4
    val isPrivate: Boolean,
    val roomCode: String?,
    val creatorName: String = "Maharana Ludo",
    val opponentName: String? = null,
    val status: String = "WAITING", // "WAITING", "PLAYING", "RESULT_SUBMITTED", "COMPLETED", "DISPUTED"
    val winnerName: String? = null,
    val expiryTime: Long = System.currentTimeMillis() + (10 * 60 * 1000), // 10 minutes expiry
    val createdAt: Long = System.currentTimeMillis(),
    val screenshotUri: String? = null,
    val disputeMessage: String? = null
)

@Entity(tableName = "tournament")
data class Tournament(
    @PrimaryKey val id: Int,
    val title: String,
    val entryFee: Double,
    val prizePool: Double,
    val joinedCount: Int,
    val maxPlayers: Int,
    val countdownMinutes: Int,
    val status: String = "JOINABLE", // "JOINABLE", "ACTIVE", "COMPLETED"
    val winnerName: String? = null
)

@Entity(tableName = "wallet_transaction")
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "DEPOSIT", "WITHDRAW", "MATCH_ENTRY", "MATCH_WIN", "REFERRAL_BONUS"
    val status: String, // "PENDING", "SUCCESS", "FAILED"
    val timestamp: Long = System.currentTimeMillis(),
    val details: String
)

@Entity(tableName = "dispute")
data class Dispute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tableId: Int,
    val claimerName: String,
    val opponentName: String,
    val screenshotUri: String?,
    val amount: Double,
    val info: String,
    val status: String = "PENDING", // "PENDING", "APPROVED_CREATOR", "APPROVED_OPPONENT", "VOIDED"
    val timestamp: Long = System.currentTimeMillis()
)
