package com.shuvostechworld.sonicmemories.di

import com.shuvostechworld.sonicmemories.data.repository.DiaryRepository
import com.shuvostechworld.sonicmemories.data.repository.DiaryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDiaryRepository(
        diaryRepositoryImpl: DiaryRepositoryImpl
    ): DiaryRepository
}
