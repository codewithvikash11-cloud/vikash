package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    // Wallet queries
    @Query("SELECT * FROM user_wallet WHERE id = 1 LIMIT 1")
    fun getUserWalletFlow(): Flow<UserWallet?>

    @Query("SELECT * FROM user_wallet WHERE id = 1 LIMIT 1")
    suspend fun getUserWalletDirect(): UserWallet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserWallet(wallet: UserWallet)

    // Table queries
    @Query("SELECT * FROM ludo_table ORDER BY createdAt DESC")
    fun getLiveTablesFlow(): Flow<List<LudoTable>>

    @Query("SELECT * FROM ludo_table WHERE id = :id LIMIT 1")
    suspend fun getTableById(id: Int): LudoTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: LudoTable): Long

    @Update
    suspend fun updateTable(table: LudoTable)

    @Delete
    suspend fun deleteTable(table: LudoTable)

    @Query("DELETE FROM ludo_table")
    suspend fun clearAllTables()

    // Tournament queries
    @Query("SELECT * FROM tournament ORDER BY entryFee ASC")
    fun getTournamentsFlow(): Flow<List<Tournament>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournaments(tournaments: List<Tournament>)

    @Update
    suspend fun updateTournament(tournament: Tournament)

    // Transaction queries
    @Query("SELECT * FROM wallet_transaction ORDER BY timestamp DESC")
    fun getTransactionsFlow(): Flow<List<WalletTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: WalletTransaction)

    // Dispute queries
    @Query("SELECT * FROM dispute ORDER BY timestamp DESC")
    fun getDisputesFlow(): Flow<List<Dispute>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispute(dispute: Dispute)

    @Update
    suspend fun updateDispute(dispute: Dispute)

    // User Account Operations
    @Query("SELECT * FROM user_account WHERE username = :username LIMIT 1")
    suspend fun getUserAccountByUsername(username: String): UserAccount?

    @Query("SELECT * FROM user_account WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getUserAccountByPhone(phone: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAccount(user: UserAccount)

    @Update
    suspend fun updateUserAccount(user: UserAccount)

    // Active Session Operations
    @Query("SELECT * FROM active_session WHERE id = 1 LIMIT 1")
    suspend fun getActiveSession(): ActiveSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveSession(session: ActiveSession)

    @Query("DELETE FROM active_session WHERE id = 1")
    suspend fun deleteActiveSession()
}
