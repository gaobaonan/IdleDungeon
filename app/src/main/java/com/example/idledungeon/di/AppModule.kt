package com.example.idledungeon.di

import android.content.Context
import com.example.idledungeon.data.SaveRepository
import com.example.idledungeon.data.database.GameDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideGameDatabase(@ApplicationContext context: Context): GameDatabase =
        GameDatabase.getInstance(context)               // 复用已有的双重检查锁单例; @Singleton 保证 Hilt 也只调一次

    @Provides @Singleton
    fun provideSaveRepository(db: GameDatabase): SaveRepository =
        SaveRepository(db)                              // db 参数会由 Hilt 自动用上面那个 Provider 注入
}