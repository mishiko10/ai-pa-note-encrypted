package com.example.data

import kotlinx.coroutines.flow.Flow

class VaultRepository(private val vaultDao: VaultDao) {
    val activeItems: Flow<List<VaultItem>> = vaultDao.getActiveItems()
    val trashItems: Flow<List<VaultItem>> = vaultDao.getTrashItems()

    suspend fun getItemById(id: Int): VaultItem? {
        return vaultDao.getItemById(id)
    }

    suspend fun insert(item: VaultItem) {
        vaultDao.insertItem(item)
    }

    suspend fun update(item: VaultItem) {
        vaultDao.updateItem(item)
    }

    suspend fun delete(item: VaultItem) {
        vaultDao.deleteItem(item)
    }

    suspend fun clearTrash() {
        vaultDao.clearTrash()
    }
}
