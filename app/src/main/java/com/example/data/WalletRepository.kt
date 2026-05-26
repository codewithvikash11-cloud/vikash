package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.random.Random

class WalletRepository(private val walletDao: WalletDao) {

    val userWallet: Flow<UserWallet?> = walletDao.getUserWalletFlow()
    val liveTables: Flow<List<LudoTable>> = walletDao.getLiveTablesFlow()
    val tournaments: Flow<List<Tournament>> = walletDao.getTournamentsFlow()
    val transactions: Flow<List<WalletTransaction>> = walletDao.getTransactionsFlow()
    val disputes: Flow<List<Dispute>> = walletDao.getDisputesFlow()

    suspend fun getWalletDirect(): UserWallet? {
        return walletDao.getUserWalletDirect()
    }

    suspend fun updateWallet(wallet: UserWallet) {
        walletDao.insertOrUpdateUserWallet(wallet)
        val session = walletDao.getActiveSession()
        if (session != null) {
            val account = walletDao.getUserAccountByUsername(session.username)
            if (account != null) {
                walletDao.insertUserAccount(
                    account.copy(
                        depositBalance = wallet.depositBalance,
                        winningBalance = wallet.winningBalance,
                        bonusBalance = wallet.bonusBalance,
                        matchesPlayed = wallet.matchesPlayed,
                        matchesWon = wallet.matchesWon,
                        totalEarnings = wallet.totalEarnings
                    )
                )
            }
        }
    }

    suspend fun createTable(entryAmount: Double, playerCount: Int, isPrivate: Boolean, customRoomCode: String? = null): Long {
        val uWallet = getWalletDirect() ?: return -1
        if (uWallet.totalBalance < entryAmount) return -2 // Insufficient funds

        // Deduct entry amount from wallet (prioritize deposit, then winning, then bonus)
        var remainingDeduct = entryAmount
        var dep = uWallet.depositBalance
        var win = uWallet.winningBalance
        var bon = uWallet.bonusBalance

        if (dep >= remainingDeduct) {
            dep -= remainingDeduct
            remainingDeduct = 0.0
        } else {
            remainingDeduct -= dep
            dep = 0.0
            if (win >= remainingDeduct) {
                win -= remainingDeduct
                remainingDeduct = 0.0
            } else {
                remainingDeduct -= win
                win = 0.0
                if (bon >= remainingDeduct) {
                    bon -= remainingDeduct
                    remainingDeduct = 0.0
                } else {
                    return -2 // Insufficient
                }
            }
        }

        val updatedWallet = uWallet.copy(
            depositBalance = dep,
            winningBalance = win,
            bonusBalance = bon,
            matchesPlayed = uWallet.matchesPlayed + 1
        )
        updateWallet(updatedWallet)

        // Generate a 6-digit room code for Ludo King
        val roomCode = customRoomCode ?: String.format("%06d", Random.nextInt(100000, 999999))
        val newTable = LudoTable(
            entryAmount = entryAmount,
            playerCount = playerCount,
            isPrivate = isPrivate,
            roomCode = roomCode,
            creatorName = uWallet.userName,
            status = "WAITING"
        )

        val tableId = walletDao.insertTable(newTable)

        // Insert Transaction log
        walletDao.insertTransaction(
            WalletTransaction(
                amount = entryAmount,
                type = "MATCH_ENTRY",
                status = "SUCCESS",
                details = "Table #$tableId Match Entry (Room $roomCode)"
            )
        )

        return tableId
    }

    suspend fun joinTable(tableId: Int): Boolean {
        val table = walletDao.getTableById(tableId) ?: return false
        val uWallet = getWalletDirect() ?: return false

        if (table.status != "WAITING") return false
        if (uWallet.totalBalance < table.entryAmount) return false

        // Deduct entry amount from wallet
        var remainingDeduct = table.entryAmount
        var dep = uWallet.depositBalance
        var win = uWallet.winningBalance
        var bon = uWallet.bonusBalance

        if (dep >= remainingDeduct) {
            dep -= remainingDeduct
            remainingDeduct = 0.0
        } else {
            remainingDeduct -= dep
            dep = 0.0
            if (win >= remainingDeduct) {
                win -= remainingDeduct
                remainingDeduct = 0.0
            } else {
                remainingDeduct -= win
                win = 0.0
                if (bon >= remainingDeduct) {
                    bon -= remainingDeduct
                    remainingDeduct = 0.0
                } else {
                    return false
                }
            }
        }

        val updatedWallet = uWallet.copy(
            depositBalance = dep,
            winningBalance = win,
            bonusBalance = bon,
            matchesPlayed = uWallet.matchesPlayed + 1
        )
        updateWallet(updatedWallet)

        // Generate room code if none existed
        val finalRoom = table.roomCode ?: String.format("%06d", Random.nextInt(100000, 999999))

        // Update table to PLAYING with current player as opponent
        val updatedTable = table.copy(
            opponentName = uWallet.userName,
            status = "PLAYING",
            roomCode = finalRoom
        )
        walletDao.updateTable(updatedTable)

        // Add transaction
        walletDao.insertTransaction(
            WalletTransaction(
                amount = table.entryAmount,
                type = "MATCH_ENTRY",
                status = "SUCCESS",
                details = "Joined Table #$tableId created by ${table.creatorName}"
            )
        )

        return true
    }

    suspend fun submitVictory(tableId: Int, screenshotUri: String? = null) {
        val table = walletDao.getTableById(tableId) ?: return
        val updatedTable = table.copy(
            status = "RESULT_SUBMITTED",
            screenshotUri = screenshotUri
        )
        walletDao.updateTable(updatedTable)
    }

    suspend fun disputeMatch(tableId: Int, disputeMsg: String, screenshotUri: String? = null) {
        val table = walletDao.getTableById(tableId) ?: return
        val updatedTable = table.copy(
            status = "DISPUTED",
            disputeMessage = disputeMsg,
            screenshotUri = screenshotUri ?: table.screenshotUri
        )
        walletDao.updateTable(updatedTable)

        // Create dispute item
        val uWallet = getWalletDirect()
        walletDao.insertDispute(
            Dispute(
                tableId = tableId,
                claimerName = uWallet?.userName ?: "Maharana Ludo",
                opponentName = table.opponentName ?: table.creatorName ?: "Opponent",
                screenshotUri = screenshotUri ?: table.screenshotUri,
                amount = table.entryAmount,
                info = disputeMsg
            )
        )
    }

    suspend fun adminResolveDispute(disputeId: Int, outcome: String) {
        // outcome: "CREATOR" (creator wins), "OPPONENT" (opponent wins), "VOID" (refund both)
        val disputesList = walletDao.getDisputesFlow().firstOrNull() ?: emptyList()
        val dispute = disputesList.find { it.id == disputeId } ?: return
        val table = walletDao.getTableById(dispute.tableId) ?: return

        val creatorWallet = if (table.creatorName == "Maharana Ludo") getWalletDirect() else null
        val opponentWallet = if (table.opponentName == "Maharana Ludo") getWalletDirect() else null

        // standard calculation: total entry pot = entryFee * 2. 
        // Prize share is 40%, house share is 60%. (Or house share is 40% / prize is 60%)
        // Let's use standard prize ratio described: 1.8x the entry pool (reflects high award split)
        val winnings = table.entryAmount * 1.8

        when (outcome) {
            "CREATOR" -> {
                // Creator wins
                walletDao.updateTable(table.copy(status = "COMPLETED", winnerName = table.creatorName))
                walletDao.updateDispute(dispute.copy(status = "APPROVED_CREATOR"))

                if (creatorWallet != null) {
                    val updated = creatorWallet.copy(
                        winningBalance = creatorWallet.winningBalance + winnings,
                        totalEarnings = creatorWallet.totalEarnings + winnings,
                        matchesWon = creatorWallet.matchesWon + 1
                    )
                    updateWallet(updated)
                    walletDao.insertTransaction(
                        WalletTransaction(
                            amount = winnings,
                            type = "MATCH_WIN",
                            status = "SUCCESS",
                            details = "Dispute Won: Match Reward on Table #${table.id}"
                        )
                    )
                }
            }
            "OPPONENT" -> {
                // Opponent wins
                walletDao.updateTable(table.copy(status = "COMPLETED", winnerName = table.opponentName))
                walletDao.updateDispute(dispute.copy(status = "APPROVED_OPPONENT"))

                if (opponentWallet != null) {
                    val updated = opponentWallet.copy(
                        winningBalance = opponentWallet.winningBalance + winnings,
                        totalEarnings = opponentWallet.totalEarnings + winnings,
                        matchesWon = opponentWallet.matchesWon + 1
                    )
                    updateWallet(updated)
                    walletDao.insertTransaction(
                        WalletTransaction(
                            amount = winnings,
                            type = "MATCH_WIN",
                            status = "SUCCESS",
                            details = "Dispute Won: Match Reward on Table #${table.id}"
                        )
                    )
                }
            }
            "VOID" -> {
                // Return entries
                walletDao.updateTable(table.copy(status = "COMPLETED", winnerName = "VOID_REFUND"))
                walletDao.updateDispute(dispute.copy(status = "VOIDED"))

                if (creatorWallet != null) {
                    updateWallet(creatorWallet.copy(depositBalance = creatorWallet.depositBalance + table.entryAmount))
                    walletDao.insertTransaction(WalletTransaction(amount = table.entryAmount, type = "DEPOSIT", status = "SUCCESS", details = "Match Void refund for Table #${table.id}"))
                }
                if (opponentWallet != null) {
                    updateWallet(opponentWallet.copy(depositBalance = opponentWallet.depositBalance + table.entryAmount))
                    walletDao.insertTransaction(WalletTransaction(amount = table.entryAmount, type = "DEPOSIT", status = "SUCCESS", details = "Match Void refund for Table #${table.id}"))
                }
            }
        }
    }

    suspend fun depositFunds(amount: Double, upiId: String, status: String = "SUCCESS"): Boolean {
        val uWallet = getWalletDirect() ?: return false
        val updated = uWallet.copy(depositBalance = uWallet.depositBalance + amount)
        updateWallet(updated)

        walletDao.insertTransaction(
            WalletTransaction(
                amount = amount,
                type = "DEPOSIT",
                status = status,
                details = "Deposited via UPI ($upiId)"
            )
        )
        return true
    }

    suspend fun withdrawFunds(amount: Double, upiId: String): Int {
        // Returns 1 for success, -1 for insufficient winning balance, -2 for minimum wage barrier
        if (amount < 300.0) return -2 // Min withdrawal ₹300

        val uWallet = getWalletDirect() ?: return -3
        if (uWallet.winningBalance < amount) return -1

        val updated = uWallet.copy(winningBalance = uWallet.winningBalance - amount)
        updateWallet(updated)

        walletDao.insertTransaction(
            WalletTransaction(
                amount = amount,
                type = "WITHDRAW",
                status = "SUCCESS",
                details = "Withdrawal to UPI $upiId"
            )
        )
        return 1
    }

    suspend fun applyReferral(code: String): Boolean {
        val uWallet = getWalletDirect() ?: return false
        if (uWallet.inviteCode == code.uppercase()) return false // cannot refer self

        // Apply bonus
        val updated = uWallet.copy(
            bonusBalance = uWallet.bonusBalance + 50.0,
            referralCount = uWallet.referralCount + 1
        )
        updateWallet(updated)

        walletDao.insertTransaction(
            WalletTransaction(
                amount = 50.0,
                type = "REFERRAL_BONUS",
                status = "SUCCESS",
                details = "Referral Bonus applied (Code $code)"
            )
        )
        return true
    }

    suspend fun banUser(ban: Boolean) {
        val uWallet = getWalletDirect() ?: return
        updateWallet(uWallet.copy(isBanned = ban))
    }

    suspend fun resetDatabase() {
        walletDao.clearAllTables()
        initializeDefaultData(force = true)
    }

    suspend fun initializeDefaultData(force: Boolean = false) {
        val existingWallet = walletDao.getUserWalletDirect()
        if (existingWallet == null || force) {
            walletDao.insertOrUpdateUserWallet(
                UserWallet(
                    id = 1,
                    userName = "Rana Pratap Ludo",
                    depositBalance = 1500.0,
                    winningBalance = 380.0,
                    bonusBalance = 50.0,
                    inviteCode = "ROYAL777",
                    referralCount = 3,
                    matchesPlayed = 28,
                    matchesWon = 19,
                    totalEarnings = 3280.0,
                    isBanned = false
                )
            )

            // Inject 10 static Tournaments
            val initialTournaments = listOf(
                Tournament(1, "Royal Clash Cup", 19.0, 19.0 * 2 * 0.4, 24, 60, 45, "JOINABLE"),
                Tournament(2, "Thunder Arena", 29.0, 29.0 * 2 * 0.4, 15, 40, 120, "JOINABLE"),
                Tournament(3, "Crown Battle League", 49.0, 49.0 * 2 * 0.4, 55, 100, 180, "JOINABLE"),
                Tournament(4, "Diamond Royale", 79.0, 79.0 * 2 * 0.4, 8, 30, 220, "JOINABLE"),
                Tournament(5, "Mega Victory Cup", 99.0, 99.0 * 2 * 0.4, 104, 200, 300, "JOINABLE"),
                Tournament(6, "Legends Tournament", 149.0, 149.0 * 2 * 0.4, 4, 20, 380, "JOINABLE"),
                Tournament(7, "Pro Master League", 199.0, 199.0 * 2 * 0.4, 11, 50, 420, "JOINABLE"),
                Tournament(8, "Elite Arena Cup", 299.0, 299.0 * 2 * 0.4, 0, 16, 600, "JOINABLE"),
                Tournament(9, "King Of Ludo", 499.0, 499.0 * 2 * 0.4, 2, 8, 800, "JOINABLE"),
                Tournament(10, "Grand Prize Showdown", 999.0, 999.0 * 2 * 0.4, 0, 4, 1440, "JOINABLE")
            )
            walletDao.insertTournaments(initialTournaments)

            // Inject some transactions
            walletDao.insertTransaction(WalletTransaction(amount = 1000.0, type = "DEPOSIT", status = "SUCCESS", details = "Initial Account Load (UPI)"))
            walletDao.insertTransaction(WalletTransaction(amount = 500.0, type = "DEPOSIT", status = "SUCCESS", details = "Weekend Top-up"))
            walletDao.insertTransaction(WalletTransaction(amount = 50.0, type = "REFERRAL_BONUS", status = "SUCCESS", details = "Referral code reward (ROYAL777)"))
            walletDao.insertTransaction(WalletTransaction(amount = 35.0, type = "MATCH_WIN", status = "SUCCESS", details = "Ludo Master table match won"))

            // Inject active live tables with real values to keep UI looking populated
            val table1 = LudoTable(
                entryAmount = 19.0,
                playerCount = 2,
                isPrivate = false,
                roomCode = "382901",
                creatorName = "Rajput_Fighter",
                status = "WAITING"
            )
            val table2 = LudoTable(
                entryAmount = 49.0,
                playerCount = 2,
                isPrivate = false,
                roomCode = "490123",
                creatorName = "Ludo_King_Jaipur",
                status = "WAITING"
            )
            val table3 = LudoTable(
                entryAmount = 1000.0,
                playerCount = 2,
                isPrivate = false,
                roomCode = "881023",
                creatorName = "Royal_Thakur",
                opponentName = "Maharana Ludo",
                status = "PLAYING"
            )
            val table4 = LudoTable(
                entryAmount = 19.0,
                playerCount = 4,
                isPrivate = true,
                roomCode = "221774",
                creatorName = "Marwar_Warrior",
                opponentName = null,
                status = "WAITING"
            )

            walletDao.insertTable(table1)
            walletDao.insertTable(table2)
            walletDao.insertTable(table3)
            walletDao.insertTable(table4)
        }
    }

    // ----------------------------------------------------
    // AUTHENTICATION AND MULTI-USER API ENGINES
    // ----------------------------------------------------

    suspend fun findAccountByPhoneOrUsername(identifier: String): UserAccount? {
        val accountByUsername = walletDao.getUserAccountByUsername(identifier)
        if (accountByUsername != null) return accountByUsername
        return walletDao.getUserAccountByPhone(identifier)
    }

    suspend fun getActiveSession(): ActiveSession? {
        return walletDao.getActiveSession()
    }

    suspend fun registerUser(
        fullName: String,
        username: String,
        phone: String,
        email: String,
        passwordRaw: String,
        referralCode: String?
    ): Boolean {
        // Double check username or phone duplication
        if (walletDao.getUserAccountByUsername(username) != null) return false
        if (walletDao.getUserAccountByPhone(phone) != null) return false

        val passwordHash = AuthHelper.hashPassword(passwordRaw)
        
        // Initial setup matching Rangilo Ludo custom default player balances
        val newAccount = UserAccount(
            username = username,
            fullName = fullName,
            phoneNumber = phone,
            email = email,
            passwordHash = passwordHash,
            referralCode = referralCode,
            depositBalance = 200.0, // initial reward balance
            winningBalance = 0.0,
            bonusBalance = 50.0,
            matchesPlayed = 0,
            matchesWon = 0,
            totalEarnings = 0.0
        )
        walletDao.insertUserAccount(newAccount)

        // Seed with referral rewards if configured
        if (!referralCode.isNullOrBlank()) {
            val updated = newAccount.copy(
                bonusBalance = newAccount.bonusBalance + 50.0
            )
            walletDao.insertUserAccount(updated)
        }

        return true
    }

    suspend fun loginUser(identifier: String, passwordRaw: String): Boolean {
        val account = findAccountByPhoneOrUsername(identifier) ?: return false
        val computedHash = AuthHelper.hashPassword(passwordRaw)
        
        if (account.passwordHash == computedHash) {
            // Generate full base64 JWT tokens
            val token = AuthHelper.generateToken(account.username, isRefresh = false)
            val rToken = AuthHelper.generateToken(account.username, isRefresh = true)
            
            // Store active session
            walletDao.insertActiveSession(
                ActiveSession(
                    id = 1,
                    username = account.username,
                    token = token,
                    refreshToken = rToken,
                    expiresAt = System.currentTimeMillis() + (60 * 60 * 1000)
                )
            )

            // Dynamic synchronization: Update active UserWallet (id = 1) so it drives all current UI
            val syncedWallet = UserWallet(
                id = 1,
                userName = account.username,
                depositBalance = account.depositBalance,
                winningBalance = account.winningBalance,
                bonusBalance = account.bonusBalance,
                inviteCode = account.username.uppercase() + "777",
                referralCount = if (!account.referralCode.isNullOrBlank()) 1 else 0,
                matchesPlayed = account.matchesPlayed,
                matchesWon = account.matchesWon,
                totalEarnings = account.totalEarnings,
                isBanned = false
            )
            walletDao.insertOrUpdateUserWallet(syncedWallet)
            
            return true
        }
        return false
    }

    suspend fun saveProfileSetup(
        username: String,
        gender: String?,
        state: String,
        avatar: String
    ): Boolean {
        val account = walletDao.getUserAccountByUsername(username) ?: return false
        val updated = account.copy(
            gender = gender,
            state = state,
            avatar = avatar
        )
        walletDao.insertUserAccount(updated)

        // Synchronize state and avatar into active UserWallet UI
        val wallet = walletDao.getUserWalletDirect()
        if (wallet != null && wallet.userName == username) {
            walletDao.insertOrUpdateUserWallet(
                wallet.copy(
                    userName = username
                )
            )
        }
        return true
    }

    suspend fun resetPassword(phone: String, passwordRaw: String): Boolean {
        val account = walletDao.getUserAccountByPhone(phone) ?: return false
        val updated = account.copy(
            passwordHash = AuthHelper.hashPassword(passwordRaw)
        )
        walletDao.insertUserAccount(updated)
        return true
    }

    suspend fun logoutUser() {
        walletDao.deleteActiveSession()
        // Standard recovery of user_wallet back to default admin-test representation
        walletDao.insertOrUpdateUserWallet(
            UserWallet(
                id = 1,
                userName = "Rana Pratap Ludo",
                depositBalance = 1500.0,
                winningBalance = 380.0,
                bonusBalance = 50.0,
                inviteCode = "ROYAL777"
            )
        )
    }

    // Refresh credentials or synchronizes active wallet back to persisted account
    suspend fun syncActiveWalletToPersistedAccount() {
        val session = walletDao.getActiveSession() ?: return
        val account = walletDao.getUserAccountByUsername(session.username) ?: return
        val wallet = walletDao.getUserWalletDirect() ?: return
        
        // Push local wallet balances to account
        val updatedAccount = account.copy(
            depositBalance = wallet.depositBalance,
            winningBalance = wallet.winningBalance,
            bonusBalance = wallet.bonusBalance,
            matchesPlayed = wallet.matchesPlayed,
            matchesWon = wallet.matchesWon,
            totalEarnings = wallet.totalEarnings
        )
        walletDao.insertUserAccount(updatedAccount)
    }
}
