package dev.alejandrorosas.core.di

import android.app.Activity

val Activity.coreComponent get() = (applicationContext as? CoreComponentProvider)?.provideCoreComponent()
