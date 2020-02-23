package dev.alejandrorosas.core.di

import android.content.Context
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [CoreModule::class, ApplicationContextModule::class])
interface CoreComponent {
    fun context(): Context
}
