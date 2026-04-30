package com.example.idledungeon.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HeroDao {
    @Query("SELECT * FROM heroes")
    suspend fun getAll(): List<HeroEntity>

    /** 整批替换所有英雄 — 先清表再插入, 简单粗暴, 一次保存即同步 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(heroes: List<HeroEntity>)

    @Query("DELETE FROM heroes")
    suspend fun clear()

    /** 用事务保证清/写原子, 避免半截状态 */
    @androidx.room.Transaction
    suspend fun replaceAll(heroes: List<HeroEntity>) {
        clear()
        if (heroes.isNotEmpty()) upsertAll(heroes)
    }
}

@Dao
interface SnapshotDao {
    /** 永远只有一行 (id=1); 没有就返回 null 表示尚未开始游戏 */
    @Query("SELECT * FROM snapshot WHERE id = 1")
    suspend fun get(): SnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: SnapshotEntity)

    @Query("DELETE FROM snapshot")
    suspend fun clear()
}
