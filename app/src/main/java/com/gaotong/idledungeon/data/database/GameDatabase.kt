package com.gaotong.idledungeon.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gaotong.idledungeon.util.L

// 改持久化字段记得把 version +1, 不然 Room schema 指纹对不上会崩.
// TODO 上线前要写 Migration 替掉 fallbackToDestructiveMigration, 现在毁库重建反正毕设没事.
@Database(
    entities = [HeroEntity::class, SnapshotEntity::class],
    version = 3,
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
