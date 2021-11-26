package com.example.androiddemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DoubleAudioDecoderTestActivity extends AppCompatActivity {

    final String TAG = getClass().getSimpleName();

    public static final int MSG_START1 = 0x1;
    public static final int MSG_START2 = 0x2;
    public static final int MSG_PAUSE1 = 0x3;
    public static final int MSG_PAUSE2 = 0x4;

    private Button mUIButtonStart1;
    private Button mUIButtonStart2;
    private Button mUIButtonPause1;
    private Button mUIButtonPause2;
    private Handler mHandler;

    private MyAudioDecoderThread mThread1;
    private MyAudioDecoderThread mThread2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_double_audio_decoder_test);
        SetupUI();
        SetupHandler();
    }

    void SetupHandler()
    {
        class MyHandler extends Handler {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch(msg.what)
                {
                    case MSG_START1:
                        if(mThread1 != null){
                            Log.d(TAG,"resume1");
                            mThread1.resume1();
                        }else{
                            Log.d(TAG,"start1");
                            mThread1 = new MyAudioDecoderThread("ADec1","/sdcard/seraphic/isobmff.mp4");
//                            mThread1 = new MyAudioDecoderThread("ADec1","/storage/F254-64AF/HTML5/isobmff2.mp4");
                            mThread1.start();
                        }
                        break;
                    case MSG_START2:
                        if(mThread2!= null)
                        {
                            Log.d(TAG,"resume2");
                            mThread1.resume1();
                        }else{
                            Log.d(TAG,"start2");
                            mThread2 = new MyAudioDecoderThread("ADec2","/sdcard/seraphic/isobmff2.mp4");
                            mThread2.start();
                        }
                        break;
                    case MSG_PAUSE1:
                        Log.d(TAG,"pause1");
                        mThread1.pause1();
                        break;
                    case MSG_PAUSE2:
                        Log.d(TAG,"pause2");
                        mThread2.pause1();
                        break;
                    default:
                        break;
                }
            }
        };
        mHandler = new MyHandler();
    }

    void SetupUI()
    {
        mUIButtonStart1 = this.findViewById(R.id.bt_start1);
        mUIButtonStart1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onclick start1");
                mHandler.sendEmptyMessage(MSG_START1);
            }
        });

        mUIButtonPause1 = this.findViewById(R.id.bt_pause1);
        mUIButtonPause1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onclick pause1");
                mHandler.sendEmptyMessage(MSG_PAUSE1);
            }
        });

        mUIButtonStart2 = this.findViewById(R.id.bt_start2);
        mUIButtonStart2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onclick start2");
                mHandler.sendEmptyMessage(MSG_START2);
            }
        });

        mUIButtonPause2 = this.findViewById(R.id.bt_pause2);
        mUIButtonPause2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onclick pause2");
                mHandler.sendEmptyMessage(MSG_PAUSE2);
            }
        });
    }

    public class MyAudioDecoderThread extends Thread {
        private String mUrl;
        private String mTag;
        private boolean mPaused = false;
        MyAudioDecoderThread(String tag, String url)
        {
            mUrl = url;
            mTag = tag;
        }
        @Override
        public void run() {
            super.run();
            StartDecode();
        }
        private void pause1()
        {
            mPaused = true;
        }
        private void resume1()
        {
            mPaused = false;
        }
        private void StartDecode()
        {
//            File MediaFile = new File(mUrl);
//            String state = Environment.getExternalStorageState(MediaFile);
//            Log.d(mTag, "state: " + state);
//            if (!state.equals(Environment.MEDIA_MOUNTED)) {
//                Log.e(mTag, "MediaFile not mounted");
//                return;
//            } else {
//                Log.d(mTag, "MediaFile mounted");
//            }

            MediaExtractor mAudioExtractor = null;
            MediaCodec mAudioDecoder = null;
            try {
                mAudioExtractor = new MediaExtractor();
                Log.d(mTag, "create extractor done");
                mAudioExtractor.setDataSource(mUrl);
                int numTracks = mAudioExtractor.getTrackCount();
                Log.d(mTag, "numTracks: " + numTracks);
                int audio_track_index = 0;
                for (int i = 0; i < numTracks; i++) {
                    MediaFormat format = mAudioExtractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio/")) {
                        audio_track_index = i;
                        Log.d(mTag, "audio track index: " + audio_track_index);
                        mAudioExtractor.selectTrack(audio_track_index);
                    }
                }
                MediaFormat mediaFormat = mAudioExtractor.getTrackFormat(audio_track_index);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                int samplerate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                Log.d(mTag, "decode audio,  mime: " + mime + ", samplerate: " + samplerate + ", channels: " + channels);

                mAudioDecoder = MediaCodec.createDecoderByType(mime);
                Log.d(mTag, "codec name: " + mAudioDecoder.getName()); // AOSP: OMX.google.aac.decoder


                mAudioDecoder.configure(mediaFormat, null, null, 0);
                mAudioDecoder.start();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;
                while(!sawOutputEOS || !sawInputEOS)
                {
                    if(mPaused)
                    {
                        Log.d(mTag, "paused");
                        try {
                        Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    if(!sawInputEOS)
                    {
                        int inputBufferId = mAudioDecoder.dequeueInputBuffer(10000);
                        if (inputBufferId >= 0) {
                            Log.d(mTag,"input buffer available idx: " + inputBufferId);
                            ByteBuffer inputBuffer = mAudioDecoder.getInputBuffer(inputBufferId);
                            int sampleSize = mAudioExtractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                Log.d(mTag, "queue EOS");
                                mAudioDecoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                                Log.d(mTag, "sawInputEOS is true");
                            } else {
                                long pts = mAudioExtractor.getSampleTime();
                                Log.d(mTag,"queue input buffer size: " + sampleSize + " pts: " + pts);
                                mAudioDecoder.queueInputBuffer(inputBufferId, 0, sampleSize, pts, 0);
                                if(!mAudioExtractor.advance()) {
                                    Log.d(mTag, "no more sample");
                                }
                            }
                        }
                    }
                    if(!sawOutputEOS)
                    {
                        int outputBufferId = mAudioDecoder.dequeueOutputBuffer(info, 10000);
                        if (outputBufferId >= 0) {
                            Log.d(mTag,"output buffer available idx: " + outputBufferId + " pts: " + info.presentationTimeUs);
                            ByteBuffer OutputBuffer = mAudioDecoder.getOutputBuffer(outputBufferId);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                                Log.d(mTag, "sawOutputEOS is true");
                            }
                            mAudioDecoder.releaseOutputBuffer(outputBufferId, false);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return ;
            }
            catch ( IllegalStateException e)
            {
                e.printStackTrace();
                return ;
            }
            finally {
                Log.d(mTag, "finally");
                if(mAudioExtractor != null)
                {
                    mAudioExtractor.release();
                }
                if(mAudioDecoder != null)
                {
                    mAudioDecoder.stop();
                    mAudioDecoder.release();
                }
            }

        }
    }
}