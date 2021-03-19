package dev.alejandrorosas.apptemplate

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.view.SurfaceHolder
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.pedro.rtplibrary.view.OpenGlView
import dagger.hilt.android.AndroidEntryPoint
import dev.alejandrorosas.apptemplate.MainViewModel.ViewState
import dev.alejandrorosas.streamlib.StreamService

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main), SurfaceHolder.Callback {

    private lateinit var sharedPreferences: SharedPreferences

    val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        viewModel.serviceLiveEvent.observe(this) { mService?.let(it) }
        viewModel.getViewState().observe(this) { render(it) }

        findViewById<View>(R.id.settings_button).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<OpenGlView>(R.id.openglview).holder.addCallback(this)
        findViewById<Button>(R.id.start_stop_service).setOnClickListener { onServiceControlClick() }
        findViewById<Button>(R.id.start_stop_stream).setOnClickListener { viewModel.onStreamControlButtonClick() }

        requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE, RECORD_AUDIO, CAMERA, WRITE_EXTERNAL_STORAGE), 1)
    }

    private fun render(viewState: ViewState) {
        findViewById<Button>(R.id.start_stop_service).setText(viewState.serviceButtonText)
        findViewById<Button>(R.id.start_stop_stream).apply {
            isVisible = viewState.streamButtonText != null
            viewState.streamButtonText?.let { setText(it) }
        }
    }

    private fun onServiceControlClick() {
        if (isMyServiceRunning(StreamService::class.java)) {
            stopService()
        } else {
            startService()
        }
    }

    private fun startService() {
        startService(
            Intent(this, StreamService::class.java).apply {
                bindService(this, connection, Context.BIND_AUTO_CREATE)
            }
        )
    }

    private fun stopService() {
        stopService(Intent(this, StreamService::class.java))
        mService = null
        unbindService(connection)
        viewModel.onStopService()
    }

    override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
//        if (mBound) {
//            mService.setView(findViewById<OpenGlView>(R.id.openglview))
//            mService.startPreview()
//        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
//        if (mBound) {
//            mService.setView(applicationContext)
//            mService.stopPreview()
//        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    private var mService: StreamService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as StreamService.LocalBinder
            mService = binder.getService().also { viewModel.onServiceConnected(it) }
//            mService.setView(findViewById<OpenGlView>(R.id.openglview))
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
            viewModel.onServiceDisconnected()
        }
    }

    override fun onStop() {
        super.onStop()
        if (mService != null) {
            unbindService(connection)
        }
    }

    @Suppress("DEPRECATION")
    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }
}
