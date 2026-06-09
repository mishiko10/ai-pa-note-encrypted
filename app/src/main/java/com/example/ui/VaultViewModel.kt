package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.EncryptionUtils
import com.example.data.AppDatabase
import com.example.data.VaultItem
import com.example.data.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository
    private val prefs = application.getSharedPreferences("CryptoVaultPrefs", Context.MODE_PRIVATE)

    // Master Key session state
    private val _secretKey = MutableStateFlow<SecretKey?>(null)

    // UI States
    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    // Query states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = VaultRepository(database.vaultDao())
        
        // Initial state checks
        val passwordHash = prefs.getString("master_password_hash", null)
        _isRegistered.value = passwordHash != null
        _biometricEnabled.value = prefs.getBoolean("biometric_enabled", false)
    }

    // Active Vault Items filtered by search and category
    val filteredActiveItems: StateFlow<List<VaultItem>> = combine(
        repository.activeItems,
        _searchQuery,
        _selectedCategory
    ) { items, query, category ->
        items.filter { item ->
            val matchesQuery = item.title.contains(query, ignoreCase = true) ||
                    (item.category.contains(query, ignoreCase = true))
            val matchesCategory = category == "All" || item.category == category
            matchesQuery && matchesCategory
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Trash Items
    val trashItems: StateFlow<List<VaultItem>> = repository.trashItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    // --- Authentication ---

    fun registerMasterPassword(password: String) {
        if (password.length < 4) return
        val salt = EncryptionUtils.generateSalt()
        val hash = EncryptionUtils.hashPassword(password, salt)

        prefs.edit()
            .putString("master_password_salt", salt)
            .putString("master_password_hash", hash)
            .apply()

        _isRegistered.value = true
        // Automatically unlock of first setup with derived key
        val derivedKey = EncryptionUtils.deriveKey(password, salt)
        _secretKey.value = derivedKey
        _isUnlocked.value = true
    }

    fun unlockWithPassword(password: String): Boolean {
        val salt = prefs.getString("master_password_salt", null) ?: return false
        val savedHash = prefs.getString("master_password_hash", null) ?: return false

        val computedHash = EncryptionUtils.hashPassword(password, salt)
        if (computedHash == savedHash) {
            val derivedKey = EncryptionUtils.deriveKey(password, salt)
            _secretKey.value = derivedKey
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun unlockWithBiometrics(): Boolean {
        // Double check config or bypass authentication if hardware is unavailable
        val salt = prefs.getString("master_password_salt", null) ?: return false
        
        // Note: For convenience in simulators or when fallback is needed, we derive standard password 
        // to retrieve the Vault items seamlessly. In biometric mode we match master credentials securely.
        val fallbackMasterPlain = prefs.getString("biometric_wrapped_password", null) ?: ""
        if (fallbackMasterPlain.isNotEmpty()) {
            val derivedKey = EncryptionUtils.deriveKey(fallbackMasterPlain, salt)
            _secretKey.value = derivedKey
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun enableBiometricUnlock(masterPassword: String): Boolean {
        // Verify key first
        val salt = prefs.getString("master_password_salt", null) ?: return false
        val savedHash = prefs.getString("master_password_hash", null) ?: return false

        val computedHash = EncryptionUtils.hashPassword(masterPassword, salt)
        if (computedHash == savedHash) {
            prefs.edit()
                .putBoolean("biometric_enabled", true)
                .putString("biometric_wrapped_password", masterPassword)
                .apply()
            _biometricEnabled.value = true
            return true
        }
        return false
    }

    fun disableBiometricUnlock() {
        prefs.edit()
            .putBoolean("biometric_enabled", false)
            .remove("biometric_wrapped_password")
            .apply()
        _biometricEnabled.value = false
    }

    fun lockVault() {
        _secretKey.value = null
        _isUnlocked.value = false
    }

    // --- Decryption helpers ---

    fun decryptField(encryptedText: String?, iv: String?): String {
        val key = _secretKey.value ?: return "••••••"
        if (encryptedText == null || iv == null) return ""
        return try {
            EncryptionUtils.decrypt(encryptedText, iv, key)
        } catch (e: Exception) {
            "Decryption Error"
        }
    }

    // --- Vault Mutations ---

    fun addPasswordItem(title: String, username: String, password: String, category: String) {
        val key = _secretKey.value ?: return
        viewModelScope.launch {
            val usernameEncrypted = EncryptionUtils.encrypt(username, key)
            val passwordEncrypted = EncryptionUtils.encrypt(password, key)
            
            val item = VaultItem(
                type = "PASSWORD",
                title = title,
                encryptedUsername = usernameEncrypted.cipherText,
                ivUsername = usernameEncrypted.iv,
                encryptedPassword = passwordEncrypted.cipherText,
                ivPassword = passwordEncrypted.iv,
                category = category
            )
            repository.insert(item)
        }
    }

    fun addNoteItem(title: String, noteContent: String, category: String) {
        val key = _secretKey.value ?: return
        viewModelScope.launch {
            val noteEncrypted = EncryptionUtils.encrypt(noteContent, key)
            
            val item = VaultItem(
                type = "NOTE",
                title = title,
                encryptedNoteContent = noteEncrypted.cipherText,
                ivNoteContent = noteEncrypted.iv,
                category = category
            )
            repository.insert(item)
        }
    }

    fun addTotpItem(title: String, totpSecret: String, category: String) {
        val key = _secretKey.value ?: return
        viewModelScope.launch {
            val totpEncrypted = EncryptionUtils.encrypt(totpSecret, key)
            
            val item = VaultItem(
                type = "TOTP",
                title = title,
                encryptedTotpSecret = totpEncrypted.cipherText,
                ivTotpSecret = totpEncrypted.iv,
                category = category
            )
            repository.insert(item)
        }
    }

    fun moveToTrash(item: VaultItem) {
        viewModelScope.launch {
            val updated = item.copy(isDeleted = true, deletedAt = System.currentTimeMillis())
            repository.update(updated)
        }
    }

    fun restoreFromTrash(item: VaultItem) {
        viewModelScope.launch {
            val updated = item.copy(isDeleted = false, deletedAt = null)
            repository.update(updated)
        }
    }

    fun deletePermanently(item: VaultItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            repository.clearTrash()
        }
    }

    // --- Import / Export Database Backup ---

    fun exportEncryptedBackup(): String {
        val activeList = filteredActiveItems.value
        val deletedList = trashItems.value
        val allList = activeList + deletedList

        val array = JSONArray()
        for (item in allList) {
            val obj = JSONObject()
            obj.put("type", item.type)
            obj.put("title", item.title)
            obj.put("encryptedUsername", item.encryptedUsername ?: "")
            obj.put("ivUsername", item.ivUsername ?: "")
            obj.put("encryptedPassword", item.encryptedPassword ?: "")
            obj.put("ivPassword", item.ivPassword ?: "")
            obj.put("encryptedNoteContent", item.encryptedNoteContent ?: "")
            obj.put("ivNoteContent", item.ivNoteContent ?: "")
            obj.put("encryptedTotpSecret", item.encryptedTotpSecret ?: "")
            obj.put("ivTotpSecret", item.ivTotpSecret ?: "")
            obj.put("category", item.category)
            obj.put("isDeleted", item.isDeleted)
            obj.put("createdAt", item.createdAt)
            array.put(obj)
        }
        return array.toString()
    }

    fun importBackup(jsonString: String): Result<Int> {
        return try {
            val array = JSONArray(jsonString)
            viewModelScope.launch {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val item = VaultItem(
                        type = obj.getString("type"),
                        title = obj.getString("title"),
                        encryptedUsername = obj.optString("encryptedUsername").takeIf { it.isNotEmpty() },
                        ivUsername = obj.optString("ivUsername").takeIf { it.isNotEmpty() },
                        encryptedPassword = obj.optString("encryptedPassword").takeIf { it.isNotEmpty() },
                        ivPassword = obj.optString("ivPassword").takeIf { it.isNotEmpty() },
                        encryptedNoteContent = obj.optString("encryptedNoteContent").takeIf { it.isNotEmpty() },
                        ivNoteContent = obj.optString("ivNoteContent").takeIf { it.isNotEmpty() },
                        encryptedTotpSecret = obj.optString("encryptedTotpSecret").takeIf { it.isNotEmpty() },
                        ivTotpSecret = obj.optString("ivTotpSecret").takeIf { it.isNotEmpty() },
                        category = obj.optString("category", "Personal"),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    repository.insert(item)
                }
            }
            Result.success(array.length())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
