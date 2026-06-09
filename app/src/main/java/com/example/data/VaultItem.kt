package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "PASSWORD", "NOTE", "TOTP"
    val title: String, // Plaintext searchable title (e.g. Google, Facebook, Personal Diary)
    val encryptedUsername: String? = null,
    val encryptedPassword: String? = null,
    val encryptedNoteContent: String? = null,
    val encryptedTotpSecret: String? = null,
    
    // Separate IVs for maximum cryptographic safety
    val ivUsername: String? = null,
    val ivPassword: String? = null,
    val ivNoteContent: String? = null,
    val ivTotpSecret: String? = null,
    
    val category: String = "Personal", // e.g. "Work", "Finance", "Social", "Others"
    val isDeleted: Boolean = false, // Trash bin Support
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
