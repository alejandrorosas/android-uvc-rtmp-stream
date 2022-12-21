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
import androidx.core.app.NotificationCompat
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera

class StreamService : Service() {
    companion object {
        private const val TAG = "RtpService"
        private const val channelId = "rtpStreamChannel"
        private const val notifyId = 123456

        var openGlView: OpenGlView? = null
    }

    val isStreaming: Boolean get() = endpoint != null
    var cameraWidth = 1280
    var cameraHeight = 960

    private var endpoint: String? = null
    private var rtmpUSB: RtmpUSB? = null
    private var uvcCamera: UVCCamera? = null
    private var usbMonitor: USBMonitor? = null
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
            val notification = NotificationCompat.Builder(this, channelId).setOngoing(true).setContentTitle("").setContentText("").build()
            startForeground(1, notification)
        } else {
            startForeground(1, Notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "RTP service started")
        usbMonitor = USBMonitor(this, onDeviceConnectListener).apply {
            register()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "RTP service destroy")
        stopStream()
        usbMonitor?.unregister()
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

    fun startStreamRtp(endpoint: String): Boolean {
        if (rtmpUSB?.isStreaming == false) {
            this.endpoint = endpoint
            if (rtmpUSB!!.prepareVideo(cameraWidth, cameraHeight, 30, 4000 * 1024, 0, uvcCamera) && rtmpUSB!!.prepareAudio()) {
                rtmpUSB!!.startStream(uvcCamera, endpoint)
                return true
            }
        }
        return false
    }

    fun setView(view: OpenGlView) {
        openGlView = view
        rtmpUSB?.replaceView(openGlView, uvcCamera)
    }

    fun setView(context: Context) {
        openGlView = null
        rtmpUSB?.replaceView(context, uvcCamera)
    }

    fun startPreview() {
        rtmpUSB?.startPreview(uvcCamera, cameraWidth, cameraHeight)
    }

    fun stopStream(force: Boolean = false) {
        if (force) endpoint = null
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

        override fun onConnectionStartedRtmp(rtmpUrl: String) {
            showNotification("On connection started")
            Log.e(TAG, "RTP On connection started")
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
        val notification =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentTitle("RTP Stream")
                .setContentText(text).build()
        notificationManager.notify(notifyId, notification)
    }

    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            usbMonitor!!.requestPermission(device)
        }

        override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
            val camera = UVCCamera()
            camera.open(ctrlBlock)
            try {
                val maxSupportedSize = camera.supportedSizeList.maxBy { it.width * it.height }
                cameraWidth = maxSupportedSize.width
                cameraHeight = maxSupportedSize.height
                camera.setPreviewSize(cameraWidth, cameraHeight, UVCCamera.FRAME_FORMAT_MJPEG)
            } catch (e: IllegalArgumentException) {
                camera.destroy()
                try {
                    camera.setPreviewSize(cameraWidth, cameraHeight, UVCCamera.DEFAULT_PREVIEW_MODE)
                } catch (e1: IllegalArgumentException) {
                    return
                }
            }
            uvcCamera = camera
            prepareStreamRtp()
            rtmpUSB!!.startPreview(uvcCamera, cameraWidth, cameraHeight)
            endpoint?.let { startStreamRtp(it) }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            stopStream(false)
        }

        override fun onCancel(device: UsbDevice?) {
        }

        override fun onDettach(device: UsbDevice?) {
            stopStream(false)
        }
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
