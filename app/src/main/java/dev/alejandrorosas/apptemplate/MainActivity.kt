package dev.alejandrorosas.apptemplate

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.SurfaceHolder
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.rtplibrary.view.OpenGlView
import dagger.hilt.android.AndroidEntryPoint
import dev.alejandrorosas.streamlib.StreamService

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main), SurfaceHolder.Callback {

    private val URL = "rtmp://192.168.0.30/publish/live"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<EditText>(R.id.et_url).setText(URL)
        findViewById<OpenGlView>(R.id.openglview).holder.addCallback(this)
        findViewById<Button>(R.id.start_stop).setOnClickListener {
            if (isMyServiceRunning(StreamService::class.java)) {
                stopStream()
            } else {
                startStream()
            }
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            1
        )
    }

    private fun startStream() {
        findViewById<Button>(R.id.start_stop).setText(R.string.stop_button)
        startService(Intent(this, StreamService::class.java).apply {
            putExtra("endpoint", findViewById<EditText>(R.id.et_url).text.toString())
            bindService(this, connection, Context.BIND_AUTO_CREATE)
        })
    }

    private fun stopStream() {
        stopService(Intent(this, StreamService::class.java))
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
        findViewById<Button>(R.id.start_stop).setText(R.string.start_button)
    }

    override fun onResume() {
        super.onResume()
        if (isMyServiceRunning(StreamService::class.java)) {
            findViewById<Button>(R.id.start_stop).setText(R.string.stop_button)
        } else {
            findViewById<Button>(R.id.start_stop).setText(R.string.start_button)
        }
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

    private lateinit var mService: StreamService
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as StreamService.LocalBinder
            mService = binder.getService()
//            mService.setView(findViewById<OpenGlView>(R.id.openglview))
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
    }

    @Suppress("DEPRECATION")
    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

