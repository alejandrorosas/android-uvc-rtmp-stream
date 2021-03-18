package dev.alejandrorosas.streamlib

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import net.ossrs.rtmp.ConnectCheckerRtmp


/**
 * Basic RTMP/RTSP service streaming implementation with camera2
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class StreamService : Service() {
    companion object {
        private const val TAG = "RtpService"
        private const val channelId = "rtpStreamChannel"
        private const val notifyId = 123456
        private const val width = 1920
        private const val height = 1080
    }

    private var endpoint: String? = null
    private var rtmpUSB: RtmpUSB? = null
    private var openGlView: OpenGlView? = null
    private var uvcCamera: UVCCamera? = null
    private lateinit var usbMonitor: USBMonitor
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "RTP service create")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        keepAliveTrick()
    }

    private fun keepAliveTrick() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, channelId)
                .setOngoing(true)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(1, notification)
        } else {
            startForeground(1, Notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "RTP service started")
        usbMonitor = USBMonitor(this, onDeviceConnectListener)
        usbMonitor.register()
        endpoint = intent?.extras?.getString("endpoint")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "RTP service destroy")
        stopStream()
        usbMonitor.unregister()
        uvcCamera?.destroy()
    }

    private fun prepareStreamRtp() {
        stopStream()
        stopPreview()

        rtmpUSB = if (openGlView == null) {
            RtmpUSB(this, connectCheckerRtmp)
        } else {
            RtmpUSB(openGlView, connectCheckerRtmp)
        }
    }

    private fun startStreamRtp(endpoint: String) {
        if (!rtmpUSB!!.isStreaming) {
            if (rtmpUSB!!.prepareVideo(width, height, 30, 4000 * 1024, false, 0, uvcCamera) && rtmpUSB!!.prepareAudio()) {
                rtmpUSB!!.startStream(uvcCamera, endpoint)
            }
        } else {
            showNotification("You are already streaming :(")
        }
    }

    fun setView(openGlView: OpenGlView) {
        this.openGlView = openGlView
//            rtmpUSB?.replaceView(openGlView)
    }

    fun setView() {
        this.openGlView = null
//            camera2Base?.replaceView(context)
    }

    fun startPreview() {
        rtmpUSB?.startPreview(uvcCamera, width, height)
    }

    fun stopStream() {
        if (rtmpUSB?.isStreaming == true) rtmpUSB!!.stopStream(uvcCamera)
    }

    fun stopPreview() {
        if (rtmpUSB?.isOnPreview == true) rtmpUSB!!.stopPreview(uvcCamera)
    }


    private val connectCheckerRtmp = object : ConnectCheckerRtmp {
        override fun onConnectionSuccessRtmp() {
            showNotification("Stream started")
            Log.e(TAG, "RTP connection success")
        }

        override fun onConnectionFailedRtmp(reason: String) {
            showNotification("Stream connection failed")
            Log.e(TAG, "RTP service destroy")
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
//            TODO("Not yet implemented")
        }

        override fun onDisconnectRtmp() {
            showNotification("Stream stopped")
        }

        override fun onAuthErrorRtmp() {
            showNotification("Stream auth error")
        }

        override fun onAuthSuccessRtmp() {
            showNotification("Stream auth success")
        }
    }

    private fun showNotification(text: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("RTP Stream")
            .setContentText(text).build()
        notificationManager.notify(notifyId, notification)
    }


    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            usbMonitor.requestPermission(device)
        }

        override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
            if (endpoint != null) {
                val camera = UVCCamera()
                camera.open(ctrlBlock)
                try {
                    camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
                } catch (e: IllegalArgumentException) {
                    camera.destroy()
                    try {
                        camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE)
                    } catch (e1: IllegalArgumentException) {
                        return
                    }
                }
                uvcCamera = camera
                prepareStreamRtp()
                rtmpUSB!!.startPreview(uvcCamera, width, height)
                startStreamRtp(endpoint!!)
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            stopStream()
        }

        override fun onCancel(device: UsbDevice?) {
        }

        override fun onDettach(device: UsbDevice?) {
            stopStream()
        }
    }

    // Binder given to clients
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): StreamService = this@StreamService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
