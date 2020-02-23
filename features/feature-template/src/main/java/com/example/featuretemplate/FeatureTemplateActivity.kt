package com.example.featuretemplate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.featuretemplate.di.DaggerFeatureTemplateComponent
import dev.alejandrorosas.core.di.coreComponent
import dev.alejandrorosas.featuretemplate.R

class FeatureTemplateActivity : AppCompatActivity(R.layout.activity_feature_template) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerFeatureTemplateComponent
            .builder()
            .coreComponent(coreComponent)
            .build()
            .inject(this)
    }
}
