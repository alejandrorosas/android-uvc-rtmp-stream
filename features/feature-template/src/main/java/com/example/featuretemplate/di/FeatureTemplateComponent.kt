package com.example.featuretemplate.di

import com.example.featuretemplate.FeatureTemplateActivity
import dagger.Component
import dev.alejandrorosas.core.di.CoreComponent
import dev.alejandrorosas.core.di.scope.ActivityScope

@ActivityScope
@Component(modules = [FeatureTemplateModule::class], dependencies = [CoreComponent::class])
interface FeatureTemplateComponent {

    fun inject(featureTemplateActivity: FeatureTemplateActivity)
}
