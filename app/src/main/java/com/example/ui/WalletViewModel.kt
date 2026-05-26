package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WalletRepository
    
    val userWallet: StateFlow<UserWallet?>
    val liveTables: StateFlow<List<LudoTable>>
    val tournaments: StateFlow<List<Tournament>>
    val transactions: StateFlow<List<WalletTransaction>>
    val disputes: StateFlow<List<Dispute>>

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.walletDao()
        repository = WalletRepository(dao)

        userWallet = repository.userWallet
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        liveTables = repository.liveTables
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        tournaments = repository.tournaments
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        transactions = repository.transactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        disputes = repository.disputes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Launch default DB preloading in a coroutine
        viewModelScope.launch {
            repository.initializeDefaultData(force = false)
            _activeSession.value = repository.getActiveSession()
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun createTable(entryAmount: Double, playerCount: Int, isPrivate: Boolean, roomCode: String?) {
        viewModelScope.launch {
            val result = repository.createTable(entryAmount, playerCount, isPrivate, roomCode)
            if (result == -2L) {
                _errorMessage.value = "Insufficient Balance to play at this Ludo Table! Please Deposit funds."
            } else if (result == -1L) {
                _errorMessage.value = "Failed to create table. Try again."
            } else {
                _successMessage.value = "Successfully Created Ludo Table! Share Room Code with your Friend."
            }
        }
    }

    fun joinTable(tableId: Int) {
        viewModelScope.launch {
            val success = repository.joinTable(tableId)
            if (success) {
                _successMessage.value = "Successfully Joined Table! Complete the Match in Ludo King, then submit the result here."
            } else {
                _errorMessage.value = "Insufficient Wallet Balance! Deposit at least ₹300 to join high-stakes tables."
            }
        }
    }

    fun submitVictory(tableId: Int, screenshotUri: String? = null) {
        viewModelScope.launch {
            repository.submitVictory(tableId, screenshotUri)
            _successMessage.value = "Result status submitted to Admin! Review details inside Disputes or Profile tabs."
        }
    }

    fun disputeMatch(tableId: Int, message: String, screenshotUri: String?) {
        viewModelScope.launch {
            repository.disputeMatch(tableId, message, screenshotUri)
            _successMessage.value = "Dispute reported! Admin panel has received the conflict report for resolution."
        }
    }

    fun depositFunds(amount: Double, upiId: String) {
        viewModelScope.launch {
            val success = repository.depositFunds(amount, upiId)
            if (success) {
                _successMessage.value = "Successfully Deposited ₹$amount! Active gaming wallet updated."
            } else {
                _errorMessage.value = "Failed to record Deposit."
            }
        }
    }

    fun withdrawFunds(amount: Double, upiId: String) {
        viewModelScope.launch {
            val code = repository.withdrawFunds(amount, upiId)
            when (code) {
                1 -> _successMessage.value = "Withdrawal request of ₹$amount successful! Settlement within 10 minutes."
                -1 -> _errorMessage.value = "Insufficient Winning Wallet balance! Direct Deposits cannot be withdrawn immediately."
                -2 -> _errorMessage.value = "Minimum withdrawal barrier is ₹300!"
                else -> _errorMessage.value = "Withdrawal Failed. Verify your request!"
            }
        }
    }

    fun applyReferral(code: String) {
        viewModelScope.launch {
            val success = repository.applyReferral(code)
            if (success) {
                _successMessage.value = "Referral code applied! ₹50 credited as Premium Gaming Bonus."
            } else {
                _errorMessage.value = "Invalid or duplicate Referral Code! You cannot refer your own account."
            }
        }
    }

    fun adminResolveDispute(disputeId: Int, outcome: String) {
        viewModelScope.launch {
            repository.adminResolveDispute(disputeId, outcome)
            _successMessage.value = "Dispute resolved successfully! Transaction settlements reflected."
        }
    }

    fun adminBanUser(ban: Boolean) {
        viewModelScope.launch {
            repository.banUser(ban)
            _successMessage.value = if (ban) "Admin: User successfully banned." else "Admin: User successfully reinstated."
        }
    }

    // ----------------------------------------------------
    // AUTHENTICATION AND MULTI-USER VM CONTROLLERS
    // ----------------------------------------------------

    fun register(
        fullName: String,
        username: String,
        phone: String,
        email: String,
        passwordRaw: String,
        referralCode: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.registerUser(
                fullName = fullName,
                username = username,
                phone = phone,
                email = email,
                passwordRaw = passwordRaw,
                referralCode = referralCode
            )
            if (success) {
                _successMessage.value = "Registered successfully, King! Authenticating..."
                onSuccess()
            } else {
                _errorMessage.value = "Username or Phone number already exists!"
            }
        }
    }

    fun login(identifier: String, passwordRaw: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (AuthHelper.isLockedOut(identifier)) {
                _errorMessage.value = "Too many failed attempts! Try again in ${AuthHelper.getRemainingLockTime(identifier)}s."
                return@launch
            }
            val success = repository.loginUser(identifier, passwordRaw)
            if (success) {
                AuthHelper.recordSuccess(identifier)
                _activeSession.value = repository.getActiveSession()
                _successMessage.value = "Namaste! Welcome back to the arena."
                onSuccess()
            } else {
                AuthHelper.recordFailedAttempt(identifier)
                if (AuthHelper.isLockedOut(identifier)) {
                    _errorMessage.value = "Too many failed attempts! Account locked for 30s."
                } else {
                    _errorMessage.value = "Invalid credentials. Please verify your phone or password."
                }
            }
        }
    }

    fun setupProfile(
        username: String,
        gender: String?,
        state: String,
        avatar: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.saveProfileSetup(username, gender, state, avatar)
            if (success) {
                _successMessage.value = "Profile synced successfully!"
                onSuccess()
            } else {
                _errorMessage.value = "Failed to synchronize profile. Please try again."
            }
        }
    }

    fun forgotPassword(phone: String, passwordRaw: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val success = repository.resetPassword(phone, passwordRaw)
            if (success) {
                _successMessage.value = "Password reset successful! Complete login with your new credentials."
                onSuccess()
            } else {
                _errorMessage.value = "Phone number is not registered in our database!"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logoutUser()
            _activeSession.value = null
            _successMessage.value = "Successfully logged out. Security session cleared."
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.resetDatabase()
            _successMessage.value = "Database has been reset completely to default royal tables!"
        }
    }
}
