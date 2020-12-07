package com.skipkou.rtmplib.rtmp.encoder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public abstract class BaseAudioPushEncoder {
    private MediaCodec.BufferInfo mAudioBuffInfo;
    private MediaCodec mAudioEncodec;
    // 通道数量，采样率，比特深度，码率
    private int channel, sampleRate, sampleBit;

    private AudioEncodecThread mAudioEncodecThread;
    private boolean encodeStart;
    private boolean audioExit;

    private AudioRecorder mAudioRecorder;

    public BaseAudioPushEncoder(Context context) {
    }

    public void start() {
        audioPts = 0;
        audioExit = false;
        encodeStart = false;

        mAudioEncodecThread = new AudioEncodecThread(new WeakReference<>(this));
        mAudioEncodecThread.start();

        mAudioRecorder.startRecord();
    }

    public void stop() {
        mAudioRecorder.stopRecord();
        mAudioRecorder = null;

        if (mAudioEncodecThread != null) {
            mAudioEncodecThread.exit();
            mAudioEncodecThread = null;
        }
        audioPts = 0;
        encodeStart = false;


    }

    /**
     * @param sampleRate 采样率
     * @param channel    通道数
     * @param sampleBit  比特深度
     * @param bitrate    码率
     */
    public void initEncoder(int sampleRate, int channel, int sampleBit, int bitrate) {
        this.sampleRate = sampleRate;
        this.sampleBit = sampleBit;
        this.channel = channel;
        initMediaEncoder(sampleRate, channel, bitrate);
    }

    private void initMediaEncoder(int sampleRate, int channel, int bitrate) {
        // aac
        initAudioEncoder(sampleRate, channel, bitrate);

        //pcm
        initPcmRecoder();
    }

    private void initAudioEncoder(int sampleRate, int channel, int bitrate) {
        try {
            mAudioEncodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channel);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096 * 10);
            mAudioEncodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mAudioBuffInfo = new MediaCodec.BufferInfo();
        } catch (IOException e) {
            e.printStackTrace();
            mAudioEncodec = null;
            mAudioBuffInfo = null;
        }
    }

    private void initPcmRecoder() {
        mAudioRecorder = new AudioRecorder();
        mAudioRecorder.setOnRecordLisener((audioData, readSize) -> {
            if (encodeStart) {
                putPcmData(audioData, readSize);
            }
        });
    }

    public void putPcmData(byte[] buffer, int size) {
        if (mAudioEncodecThread != null && !mAudioEncodecThread.isExit && buffer != null && size > 0) {
            int inputBufferIndex = mAudioEncodec.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer byteBuffer = mAudioEncodec.getInputBuffers()[inputBufferIndex];
                byteBuffer.clear();
                byteBuffer.put(buffer);
                long pts = getAudioPts(size, sampleRate, channel, sampleBit);
                mAudioEncodec.queueInputBuffer(inputBufferIndex, 0, size, pts, 0);
            }
        }
    }


    private long audioPts;

    private long getAudioPts(int size, int sampleRate, int channel, int sampleBit) {
        audioPts += (long) (1.0 * size / (sampleRate * channel * (sampleBit / 8)) * 1000000.0);
        return audioPts;
    }

    static class AudioEncodecThread extends Thread {
        private WeakReference<BaseAudioPushEncoder> encoderWeakReference;
        private boolean isExit;

        private MediaCodec audioEncodec;
        private MediaCodec.BufferInfo audioBufferinfo;

        private long pts;


        public AudioEncodecThread(WeakReference<BaseAudioPushEncoder> encoderWeakReference) {
            this.encoderWeakReference = encoderWeakReference;
            audioEncodec = encoderWeakReference.get().mAudioEncodec;
            audioBufferinfo = encoderWeakReference.get().mAudioBuffInfo;
            pts = 0;
        }


        @Override
        public void run() {
            super.run();
            isExit = false;
            audioEncodec.start();

            while (true) {
                if (isExit) {
                    audioEncodec.stop();
                    audioEncodec.release();
                    audioEncodec = null;
                    encoderWeakReference.get().audioExit = true;
                    break;
                }

                int outputBufferIndex = audioEncodec.dequeueOutputBuffer(audioBufferinfo, 0);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!encoderWeakReference.get().encodeStart) {
                        encoderWeakReference.get().encodeStart = true;
                    }

                } else {
                    while (outputBufferIndex >= 0) {
                        if (!encoderWeakReference.get().encodeStart) {
                            SystemClock.sleep(5);
                            continue;
                        }

                        ByteBuffer outputBuffer = audioEncodec.getOutputBuffers()[outputBufferIndex];
                        outputBuffer.position(audioBufferinfo.offset);
                        outputBuffer.limit(audioBufferinfo.offset + audioBufferinfo.size);

                        //设置时间戳
                        if (pts == 0) {
                            pts = audioBufferinfo.presentationTimeUs;
                        }
                        audioBufferinfo.presentationTimeUs = audioBufferinfo.presentationTimeUs - pts;

                        //写入数据
                        byte[] data = new byte[outputBuffer.remaining()];
                        outputBuffer.get(data, 0, data.length);
                        if (encoderWeakReference.get().onMediaInfoListener != null) {
                            encoderWeakReference.get().onMediaInfoListener.onAudioInfo(data);
                        }

                        audioEncodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = audioEncodec.dequeueOutputBuffer(audioBufferinfo, 0);
                    }
                }

            }

        }

        public void exit() {
            isExit = true;
        }
    }

    private OnMediaInfoListener onMediaInfoListener;

    public void setOnMediaInfoListener(OnMediaInfoListener onMediaInfoListener) {
        this.onMediaInfoListener = onMediaInfoListener;
    }

    public interface OnMediaInfoListener {
        void onAudioInfo(byte[] data);
    }

    public interface OnStatusChangeListener {
        void onStatusChange(STATUS status);

        enum STATUS {
            INIT,
            START,
            END
        }
    }

}
