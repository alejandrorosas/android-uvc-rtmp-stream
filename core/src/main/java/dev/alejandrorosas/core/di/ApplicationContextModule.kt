package dev.alejandrorosas.core.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ApplicationContextModule(private val application: Application) {

    @Singleton
    @Provides
    fun provideContext(): Context = application
}
