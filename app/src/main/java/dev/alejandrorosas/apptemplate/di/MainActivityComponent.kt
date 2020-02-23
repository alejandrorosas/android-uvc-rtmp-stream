package dev.alejandrorosas.apptemplate.di

import dagger.Component
import dev.alejandrorosas.apptemplate.MainActivity
import dev.alejandrorosas.core.di.CoreComponent
import dev.alejandrorosas.core.di.scope.ActivityScope

@ActivityScope
@Component(modules = [MainActivityModule::class], dependencies = [CoreComponent::class])
interface MainActivityComponent {
    fun inject(mainActivity: MainActivity)
}
