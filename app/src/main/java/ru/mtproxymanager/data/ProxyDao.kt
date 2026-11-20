package ru.mtproxymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {

    @Query("SELECT * FROM proxies")
    fun observeAll(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies")
    suspend fun getAll(): List<ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ProxyEntity>): List<Long>

    @Query("UPDATE proxies SET pingMs = :pingMs, protocolOk = :protocolOk, checkedAt = :checkedAt, failCount = :failCount WHERE id = :id")
    suspend fun updateCheck(id: String, pingMs: Int?, protocolOk: Boolean, checkedAt: Long, failCount: Int)

    @Query("UPDATE proxies SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM proxies WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
