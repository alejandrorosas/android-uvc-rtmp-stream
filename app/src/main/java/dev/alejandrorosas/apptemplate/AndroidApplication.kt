package dev.alejandrorosas.apptemplate

import android.app.Application
import dev.alejandrorosas.core.di.ApplicationContextModule
import dev.alejandrorosas.core.di.CoreComponent
import dev.alejandrorosas.core.di.CoreComponentProvider
import dev.alejandrorosas.core.di.DaggerCoreComponent

class AndroidApplication : Application(), CoreComponentProvider {

    private val coreComponent: CoreComponent by lazy(mode = LazyThreadSafetyMode.NONE) {
        DaggerCoreComponent.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .build()
    }

    override fun provideCoreComponent() = coreComponent
}
