package com.example.idledungeon.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.idledungeon.util.L

/**
 * 全局唯一数据库. App.onCreate 时通过 [getInstance] 创建一次, 后续直接复用.
 *
 * 改 schema (加字段 / 加表) 必须做以下任一:
 *   1. 把 version 提升, 写一个 Migration 处理 ALTER TABLE
 *   2. 调用 .fallbackToDestructiveMigration() — 直接清库重建 (毕设期间数据可丢, 用这个)
 */
@Database(
    entities = [HeroEntity::class, SnapshotEntity::class],
    // 每次改动持久化字段 (HeroEntity / SnapshotEntity / Converters 涉及的类型) 都必须 +1,
    // 否则 Room schema 指纹不匹配会直接抛 IllegalStateException (fallback 只在版本变化时才触发).
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GameDatabase : RoomDatabase() {

    abstract fun heroDao(): HeroDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile private var INSTANCE: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "idle_dungeon.db"
                )
                    // 毁掉旧库的回调: 仅当 fallbackToDestructiveMigration 触发时调用
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also {
                        INSTANCE = it
                        L.i("DB", "GameDatabase 实例已创建 (path=idle_dungeon.db)")
                    }
            }
        }
    }
}
