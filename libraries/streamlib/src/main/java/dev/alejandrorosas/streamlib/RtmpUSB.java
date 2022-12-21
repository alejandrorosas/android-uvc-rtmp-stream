package dev.alejandrorosas.streamlib;

import android.content.Context;
import android.media.MediaCodec;

import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;

import java.nio.ByteBuffer;

public class RtmpUSB extends USBBase {

    private final RtmpClient rtmpClient;

    public RtmpUSB(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
        super(openGlView);
        rtmpClient = new RtmpClient(connectChecker);
    }

    public RtmpUSB(LightOpenGlView lightOpenGlView, ConnectCheckerRtmp connectChecker) {
        super(lightOpenGlView);
        rtmpClient = new RtmpClient(connectChecker);
    }

    public RtmpUSB(Context context, ConnectCheckerRtmp connectChecker) {
        super(context);
        rtmpClient = new RtmpClient(connectChecker);
    }

    @Override
    public void setAuthorization(String user, String password) {
        rtmpClient.setAuthorization(user, password);
    }

    @Override
    protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
        rtmpClient.setAudioInfo(sampleRate, isStereo);
    }

    @Override
    protected void startStreamRtp(String url) {
        if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
            rtmpClient.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
        } else {
            rtmpClient.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
        }
        rtmpClient.connect(url);
    }

    @Override
    protected void stopStreamRtp() {
        rtmpClient.disconnect();
    }

    @Override
    protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        rtmpClient.sendAudio(aacBuffer, info);
    }

    @Override
    protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        rtmpClient.setVideoInfo(sps, pps, vps);
    }

    @Override
    protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        rtmpClient.sendVideo(h264Buffer, info);
    }
}

