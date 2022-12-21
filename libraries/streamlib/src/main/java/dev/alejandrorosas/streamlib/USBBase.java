package dev.alejandrorosas.streamlib;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;
import com.serenegiant.usb.UVCCamera;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wrapper to stream with camera1 api and microphone. Support stream with SurfaceView, TextureView
 * and OpenGlView(Custom SurfaceView that use OpenGl). SurfaceView and TextureView use buffer to
 * buffer encoding mode for H264 and OpenGlView use Surface to buffer mode(This mode is generally
 * better because skip buffer processing).
 * <p>
 * API requirements:
 * SurfaceView and TextureView mode: API 16+.
 * OpenGlView: API 18+.
 * <p>
 * Created by pedro on 7/07/17.
 */

public abstract class USBBase implements GetAacData, GetCameraData, GetVideoData, GetMicrophoneData {

    private static final String TAG = "Camera1Base";

    private Context context;
    protected VideoEncoder videoEncoder;
    private MicrophoneManager microphoneManager;
    private AudioEncoder audioEncoder;
    private GlInterface glInterface;
    private boolean streaming = false;
    private boolean videoEnabled = true;
    //record
    private MediaMuxer mediaMuxer;
    private int videoTrack = -1;
    private int audioTrack = -1;
    private boolean recording = false;
    private boolean canRecord = false;
    private boolean onPreview = false;
    private MediaFormat videoFormat;
    private MediaFormat audioFormat;

    public USBBase(OpenGlView openGlView) {
        context = openGlView.getContext();
        this.glInterface = openGlView;
        this.glInterface.init();
        init();
    }

    public USBBase(LightOpenGlView lightOpenGlView) {
        context = lightOpenGlView.getContext();
        this.glInterface = lightOpenGlView;
        this.glInterface.init();
        init();
    }

    public USBBase(Context context) {
        this.context = context;
        glInterface = new OffScreenGlThread(context);
        glInterface.init();
        init();
    }

    private void init() {
        videoEncoder = new VideoEncoder(this);
        setMicrophoneMode(MicrophoneMode.SYNC);
    }

    /**
     * Basic auth developed to work with Wowza. No tested with other server
     *
     * @param user     auth.
     * @param password auth.
     */
    public abstract void setAuthorization(String user, String password);

    /**
     * Call this method before use @startStream. If not you will do a stream without video. NOTE:
     * Rotation with encoder is silence ignored in some devices.
     *
     * @param width    resolution in px.
     * @param height   resolution in px.
     * @param fps      frames per second of the stream.
     * @param bitrate  H264 in kb.
     * @param rotation could be 90, 180, 270 or 0. You should use CameraHelper.getCameraOrientation
     *                 with SurfaceView or TextureView and 0 with OpenGlView or LightOpenGlView. NOTE: Rotation with
     *                 encoder is silence ignored in some devices.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval, int rotation, UVCCamera uvcCamera) {
        if (onPreview) {
            stopPreview(uvcCamera);
            onPreview = true;
        }
        return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, iFrameInterval, FormatVideoEncoder.SURFACE);
    }

    /**
     * backward compatibility reason
     */
    public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation, UVCCamera uvcCamera) {
        return prepareVideo(width, height, fps, bitrate, 2, rotation, uvcCamera);
    }

    protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

    /**
     * Call this method before use @startStream. If not you will do a stream without audio.
     *
     * @param bitrate         AAC in kb.
     * @param sampleRate      of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
     * @param isStereo        true if you want Stereo audio (2 audio channels), false if you want Mono audio
     *                        (1 audio channel).
     * @param echoCanceler    true enable echo canceler, false disable.
     * @param noiseSuppressor true enable noise suppressor, false  disable.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio(
            int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
            boolean noiseSuppressor) {
        microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
        prepareAudioRtp(isStereo, sampleRate);
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo, microphoneManager.getMaxInputSize());
    }

    /**
     * Same to call: prepareAudio(64 * 1024, 32000, true, false, false);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio() {
        return prepareAudio(64 * 1024, 32000, true, false, false);
    }

    /**
     * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
        videoEncoder.setForce(forceVideo);
        audioEncoder.setForce(forceAudio);
    }

    /**
     * Start record a MP4 video. Need be called while stream.
     *
     * @param path where file will be saved.
     * @throws IOException If you init it before start stream.
     */
    public void startRecord(UVCCamera uvcCamera, final String path) throws IOException {
        mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        recording = true;
        if (!streaming) {
            startEncoders(uvcCamera);
        } else if (videoEncoder.isRunning()) {
            resetVideoEncoder();
        }
    }

    /**
     * Stop record MP4 video started with @startRecord. If you don't call it file will be unreadable.
     */
    public void stopRecord(UVCCamera uvcCamera) {
        recording = false;
        if (mediaMuxer != null) {
            if (canRecord) {
                mediaMuxer.stop();
                mediaMuxer.release();
                canRecord = false;
            }
            mediaMuxer = null;
        }
        videoTrack = -1;
        audioTrack = -1;
        if (!streaming) {
            stopStream(uvcCamera);
        }
    }

    /**
     * Start camera preview. Ignored, if stream or preview is started.
     *
     * @param width  of preview in px.
     * @param height of preview in px.
     */
    public void startPreview(final UVCCamera uvcCamera, int width, int height) {
        if (!isStreaming() && !onPreview && !(glInterface instanceof OffScreenGlThread)) {
            glInterface.setEncoderSize(width, height);
            glInterface.setRotation(0);
            glInterface.start();
            uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
            uvcCamera.startPreview();
            onPreview = true;
        } else {
            Log.e(TAG, "Streaming or preview started, ignored");
        }
    }

    /**
     * Stop camera preview. Ignored if streaming or already stopped. You need call it after
     *
     * @stopStream to release camera properly if you will close activity.
     */
    public void stopPreview(UVCCamera uvcCamera) {
        if (!isStreaming() && onPreview && !(glInterface instanceof OffScreenGlThread)) {
            if (glInterface != null) {
                glInterface.stop();
            }
            uvcCamera.stopPreview();
            onPreview = false;
        } else {
            Log.e(TAG, "Streaming or preview stopped, ignored");
        }
    }

    protected abstract void startStreamRtp(String url);

    /**
     * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
     *
     * @param url of the stream like: protocol://ip:port/application/streamName
     *            <p>
     *            RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
     *            RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
     * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
     * startPreview for you to resolution seated in @prepareVideo.
     */
    public void startStream(UVCCamera uvcCamera, String url) {
        streaming = true;
        if (!recording) {
            startEncoders(uvcCamera);
        } else {
            resetVideoEncoder();
        }
        startStreamRtp(url);
        onPreview = true;
    }

    private void startEncoders(UVCCamera uvcCamera) {
        videoEncoder.start();
        audioEncoder.start();
        microphoneManager.start();

        uvcCamera.stopPreview();
        glInterface.stop();
        glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
        glInterface.setRotation(0);
        glInterface.start();
        uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
        uvcCamera.startPreview();

        if (videoEncoder.getInputSurface() != null) {
            glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        }

        onPreview = true;
    }

    private void resetVideoEncoder() {
        if (glInterface != null) {
            glInterface.removeMediaCodecSurface();
        }
        videoEncoder.reset();
        if (glInterface != null) {
            glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        }
    }

    private void prepareGlView() {
        if (glInterface != null) {
            if (glInterface instanceof OffScreenGlThread) {
                glInterface = new OffScreenGlThread(context);
                ((OffScreenGlThread) glInterface).setFps(videoEncoder.getFps());
            }
            glInterface.init();
            if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
                glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
            } else {
                glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
            }
            glInterface.start();
            if (videoEncoder.getInputSurface() != null) {
                glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
            }

        }
    }

    protected abstract void stopStreamRtp();

    /**
     * Stop stream started with @startStream.
     */
    public void stopStream(UVCCamera uvcCamera) {
        if (streaming) {
            streaming = false;
            stopStreamRtp();
        }
        if (!recording) {
            microphoneManager.stop();
            if (glInterface != null) {
                glInterface.removeMediaCodecSurface();
                if (glInterface instanceof OffScreenGlThread) {
                    glInterface.stop();
                    uvcCamera.stopPreview();
                }
            }
            videoEncoder.stop();
            audioEncoder.stop();
            videoFormat = null;
            audioFormat = null;
        }
    }

    /**
     * Mute microphone, can be called before, while and after stream.
     */
    public void disableAudio() {
        microphoneManager.mute();
    }

    /**
     * Enable a muted microphone, can be called before, while and after stream.
     */
    public void enableAudio() {
        microphoneManager.unMute();
    }

    /**
     * Get mute state of microphone.
     *
     * @return true if muted, false if enabled
     */
    public boolean isAudioMuted() {
        return microphoneManager.isMuted();
    }

    /**
     * Get video camera state
     *
     * @return true if disabled, false if enabled
     */
    public boolean isVideoEnabled() {
        return videoEnabled;
    }

    public int getBitrate() {
        return videoEncoder.getBitRate();
    }

    public int getResolutionValue() {
        return videoEncoder.getWidth() * videoEncoder.getHeight();
    }

    public int getStreamWidth() {
        return videoEncoder.getWidth();
    }

    public int getStreamHeight() {
        return videoEncoder.getHeight();
    }

    public GlInterface getGlInterface() {
        if (glInterface != null) {
            return glInterface;
        } else {
            throw new RuntimeException("You can't do it. You are not using Opengl");
        }
    }

    /**
     * Set video bitrate of H264 in kb while stream.
     *
     * @param bitrate H264 in kb.
     */
    public void setVideoBitrateOnFly(int bitrate) {
        videoEncoder.setVideoBitrateOnFly(bitrate);
    }

    /**
     * Set limit FPS while stream. This will be override when you call to prepareVideo method. This
     * could produce a change in iFrameInterval.
     *
     * @param fps frames per second
     */
    public void setLimitFPSOnFly(int fps) {
        videoEncoder.setFps(fps);
    }

    /**
     * Get stream state.
     *
     * @return true if streaming, false if not streaming.
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Get preview state.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isOnPreview() {
        return onPreview;
    }

    /**
     * Get record state.
     *
     * @return true if recording, false if not recoding.
     */
    public boolean isRecording() {
        return recording;
    }

    protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

    @Override
    public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        if (recording && canRecord) {
            mediaMuxer.writeSampleData(audioTrack, aacBuffer, info);
        }
        if (streaming) {
            getAacDataRtp(aacBuffer, info);
        }
    }

    protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        if (streaming) {
            onSpsPpsVpsRtp(sps, pps, vps);
        }
    }

    protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

    @Override
    public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        if (recording) {
            if (info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
                && !canRecord
                && videoFormat != null
                && audioFormat != null) {
                videoTrack = mediaMuxer.addTrack(videoFormat);
                audioTrack = mediaMuxer.addTrack(audioFormat);
                mediaMuxer.start();
                canRecord = true;
            }
            if (canRecord) {
                mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
            }
        }
        if (streaming) {
            getH264DataRtp(h264Buffer, info);
        }
    }

    @Override
    public void inputPCMData(Frame frame) {
        audioEncoder.inputPCMData(frame);
    }

    @Override
    public void inputYUVData(Frame frame) {
        videoEncoder.inputYUVData(frame);
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {
        videoFormat = mediaFormat;
    }

    @Override
    public void onAudioFormat(MediaFormat mediaFormat) {
        audioFormat = mediaFormat;
    }

    /**
     * Must be called before prepareAudio.
     *
     * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
     *                       SYNC using same thread. This mode could solve choppy audio or audio frame discarded.
     *                       ASYNC using other thread.
     */
    public void setMicrophoneMode(MicrophoneMode microphoneMode) {
        switch (microphoneMode) {
            case SYNC:
                microphoneManager = new MicrophoneManagerManual();
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
                audioEncoder.setTsModeBuffer(false);
                break;
            case ASYNC:
                microphoneManager = new MicrophoneManager(this);
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setTsModeBuffer(false);
                break;
            case BUFFER:
                microphoneManager = new MicrophoneManager(this);
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setTsModeBuffer(true);
                break;
        }
    }

    public void replaceView(Context context, UVCCamera uvcCamera) {
        replaceGlInterface(new OffScreenGlThread(context), uvcCamera);
    }

    public void replaceView(OpenGlView openGlView, UVCCamera uvcCamera) {
        replaceGlInterface(openGlView, uvcCamera);
    }

    public void replaceView(LightOpenGlView lightOpenGlView, UVCCamera uvcCamera) {
        replaceGlInterface(lightOpenGlView, uvcCamera);
    }

    /**
     * Replace glInterface used on fly. Ignored if you use SurfaceView or TextureView
     */
    private void replaceGlInterface(GlInterface glInterface, UVCCamera uvcCamera) {
        if (this.glInterface != null) {
            if (isStreaming() || isRecording() || isOnPreview()) {
                uvcCamera.stopPreview();
                this.glInterface.removeMediaCodecSurface();
                this.glInterface.stop();
                this.glInterface = glInterface;
                this.glInterface.init();
                this.glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
                this.glInterface.setRotation(0);
                this.glInterface.start();
                if (isStreaming() || isRecording()) {
                    this.glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
                }
                uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
                uvcCamera.startPreview();
            } else {
                this.glInterface = glInterface;
            }
        }
    }
}
