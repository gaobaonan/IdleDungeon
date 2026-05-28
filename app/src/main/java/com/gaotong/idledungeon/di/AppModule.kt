package com.gaotong.idledungeon.di

import android.content.Context
import com.gaotong.idledungeon.data.RoomSaveRepository
import com.gaotong.idledungeon.data.SaveRepository
import com.gaotong.idledungeon.data.database.GameDatabase
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
        GameDatabase.getInstance(context)

    @Provides @Singleton
    fun provideSaveRepository(db: GameDatabase): SaveRepository =
        RoomSaveRepository(db)
}