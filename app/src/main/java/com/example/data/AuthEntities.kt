package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_account")
data class UserAccount(
    @PrimaryKey val username: String,
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val passwordHash: String,
    val referralCode: String?,
    val state: String = "Rajasthan",
    val gender: String? = null,
    val avatar: String = "👑",
    val depositBalance: Double = 500.0,
    val winningBalance: Double = 0.0,
    val bonusBalance: Double = 50.0,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val totalEarnings: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "active_session")
data class ActiveSession(
    @PrimaryKey val id: Int = 1,
    val username: String,
    val token: String,
    val refreshToken: String,
    val expiresAt: Long
)
