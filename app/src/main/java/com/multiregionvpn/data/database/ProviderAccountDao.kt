package com.multiregionvpn.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderAccountDao {
    @Query("SELECT * FROM provider_accounts")
    fun getAllAccounts(): Flow<List<ProviderAccountEntity>>

    @Query("SELECT * FROM provider_accounts WHERE providerId = :providerId")
    fun getAccountsByProvider(providerId: String): Flow<List<ProviderAccountEntity>>

    @Query("SELECT * FROM provider_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): ProviderAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: ProviderAccountEntity)

    @Update
    suspend fun updateAccount(account: ProviderAccountEntity)

    @Delete
    suspend fun deleteAccount(account: ProviderAccountEntity)

    @Query("DELETE FROM provider_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)
}


