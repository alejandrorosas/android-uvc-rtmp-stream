package dev.alejandrorosas.apptemplate

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.SurfaceHolder
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import com.pedro.rtplibrary.view.OpenGlView
import dagger.hilt.android.AndroidEntryPoint
import dev.alejandrorosas.apptemplate.MainViewModel.ViewState
import dev.alejandrorosas.streamlib.StreamService

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main), SurfaceHolder.Callback, ServiceConnection {

    private val viewModel by viewModels<MainViewModel>()
    private var mService: StreamService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StreamService.openGlView = findViewById(R.id.openglview)
        startService(getServiceIntent())

        viewModel.serviceLiveEvent.observe(this) { mService?.let(it) }
        viewModel.getViewState().observe(this) { render(it) }

        findViewById<View>(R.id.settings_button).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<OpenGlView>(R.id.openglview).holder.addCallback(this)
        findViewById<Button>(R.id.start_stop_stream).setOnClickListener { viewModel.onStreamControlButtonClick() }

        requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE, RECORD_AUDIO, CAMERA, WRITE_EXTERNAL_STORAGE), 1)
    }

    private fun render(viewState: ViewState) {
        findViewById<Button>(R.id.start_stop_stream).setText(viewState.streamButtonText)
    }

    private fun getServiceIntent(): Intent {
        return Intent(this, StreamService::class.java).also {
            bindService(it, this, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopService() {
        stopService(Intent(this, StreamService::class.java))
        mService = null
        unbindService(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        mService?.let {
            it.setView(findViewById<OpenGlView>(R.id.openglview))
            it.startPreview()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mService?.let {
            it.setView(applicationContext)
            it.stopPreview()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        mService = (service as StreamService.LocalBinder).getService()
    }

    override fun onServiceDisconnected(arg0: ComponentName) {
        mService = null
    }

    override fun onStop() {
        super.onStop()
        unbindService(this)
    }

    override fun onStart() {
        super.onStart()
        getServiceIntent()
    }

    override fun onDestroy() {
        if (mService?.isStreaming == false) stopService()
        super.onDestroy()
    }
}
