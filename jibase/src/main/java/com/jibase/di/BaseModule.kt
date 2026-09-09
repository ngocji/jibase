package com.jibase.di

import android.content.Context
import com.jibase.pref.DataStoreHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class BaseModule {
    @Provides
    @Singleton
    fun providerDefaultDataStoreHelper(
        @ApplicationContext context: Context,
        @DefaultPrefName name: String
    ): DataStoreHelper {
        return DataStoreHelper(context, name)
    }
}