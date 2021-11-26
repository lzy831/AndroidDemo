package com.example.androiddemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

//import android.media.tv.tuner;
//import android.media.tv.tuner.filter.Filter;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.graphics.Color.BLACK;
import static android.media.AudioTrack.WRITE_BLOCKING;
import static java.lang.Boolean.TRUE;

public class TunnelModeTestActivity extends AppCompatActivity {

    final String TAG = "TunnelModeTestActivity";

    // UI
    private SurfaceView mUIVideoSurface;
    private Button mUIButtonStart;
    private Button mUIButtonStop;

    private Handler mHandler;
    private Context mContext;
    private AudioTrack mAudioTrack;
    private MediaCodec mMediaCodec;
    int mAudioSessionId;

    private static final long DEFAULT_TIMEOUT_US = 10000;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        verifyStoragePermissions(this);
        SetupUI();
        SetupHandler();

        mContext = getApplicationContext();
        AudioManager am = (AudioManager) mContext.getSystemService(AUDIO_SERVICE);
        mAudioSessionId = am.generateAudioSessionId();
        Log.d(TAG,"mAudioSessionId: " + mAudioSessionId);

    }

    public static final int MSG_START = 0x1;
    public static final int MSG_STOP = 0x2;

    void SetupHandler()
    {
        class MyHandler extends Handler {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch(msg.what)
                {
                    case MSG_START:
                        Log.d(TAG,"start");
//                        CreateAudioTrack2();
//                        CreateMediaCodec();
                        new MyAudioThread().start();
                        new MyVideoThread().start();
                        break;

                    case MSG_STOP:
                        Log.d(TAG,"stop");
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
        mUIVideoSurface = this.findViewById(R.id.sf_video);
        mUIButtonStart = this.findViewById(R.id.bt_start);
        mUIButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onclick start");
                mHandler.sendEmptyMessage(MSG_START);
            }
        });

        mUIButtonStop = this.findViewById(R.id.bt_stop);
        mUIButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"onclick stop");
                mHandler.sendEmptyMessage(MSG_STOP);
            }
        });
    }


    public class MyAudioThread extends Thread{
        @Override
        public void run() {
            super.run();
            CreateAudioTrack2();
        }
    }
    public class MyVideoThread extends Thread{
        @Override
        public void run() {
            super.run();
            CreateMediaCodec();
        }
    }

    void CreateAudioTrack() {

        String MediaVideoPath = "/sdcard/seraphic/Channel_ID_voices_321_ddp_A.mp4";
//        String MediaVideoPath = "/sdcard/seraphic/trailer.mp4";

        File MediaFile = new File(MediaVideoPath);
        String state = Environment.getExternalStorageState(MediaFile);
        Log.d(TAG, "state: " + state);
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            Log.e(TAG, "MediaFile not mounted");
            return;
        } else {
            Log.d(TAG, "MediaFile mounted");
        }

        MediaExtractor mAudioExtractor = new MediaExtractor();
        try {
            Log.d(TAG, "create extractor");

            mAudioExtractor.setDataSource(MediaVideoPath);
            int numTracks = mAudioExtractor.getTrackCount();
            Log.d(TAG, "numTracks: " + numTracks);

            int audio_track_index = 0;
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = mAudioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audio_track_index = i;
                    Log.d(TAG, "audio track index: " + audio_track_index);
                    mAudioExtractor.selectTrack(audio_track_index);
                }
            }

            MediaFormat mediaFormat = mAudioExtractor.getTrackFormat(audio_track_index);

            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

            int samplerate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            Log.d(TAG, "decode audio,  mime: " + mime + ", samplerate: " + samplerate + ", channels: " + channels);


            AudioAttributes.Builder aab = new AudioAttributes.Builder();
            aab.setContentType(AudioAttributes.CONTENT_TYPE_MOVIE);
            aab.setFlags(AudioAttributes.FLAG_HW_AV_SYNC);
            aab.setUsage(AudioAttributes.USAGE_MEDIA);
            AudioAttributes aa = aab.build();

            AudioFormat.Builder afb = new AudioFormat.Builder();
            afb.setEncoding(AudioFormat.ENCODING_AC3);
            afb.setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1);
            afb.setSampleRate(samplerate);
//            afb.setEncoding();
            AudioFormat af = afb.build();

            AudioTrack.Builder atb = new AudioTrack.Builder();
            atb.setAudioAttributes(aa);
            atb.setAudioFormat(af);
            atb.setTransferMode(AudioTrack.MODE_STREAM);

            mAudioTrack = atb.build();
            mAudioTrack.play();


            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            boolean stop = false;





            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4096);

            while (!stop && !sawOutputEOS) {

                if (!sawInputEOS) {

                    int sampleSize = mAudioExtractor.readSampleData(byteBuffer, 0);
                    if (sampleSize < 0) {
                        Log.d(TAG, "sawInputEOS is true");
                        sawInputEOS = true;
                    } else {
                        int write_size = mAudioTrack.write(byteBuffer, sampleSize, WRITE_BLOCKING);
                        Log.d(TAG, "write audio wirte_size:" + write_size);

                        if(!mAudioExtractor.advance())
                        {
                            Log.d(TAG, "no more sample");
                        }
                    }
                }

                // no need to dequeue explicitly output buffers. The codec
                // does this directly to the sideband layer.

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mAudioTrack != null) {
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
            if (mAudioExtractor != null) {
                mAudioExtractor.release();
                mAudioExtractor = null;
            }
        }
    }

    void CreateAudioTrack2() {



//        int encoding = AudioFormat.ENCODING_AC4;
//        int channelmask = AudioFormat.CHANNEL_OUT_STEREO;
//        int samplerate = 48000;

        int encoding = AudioFormat.ENCODING_E_AC3;
        int channelmask = AudioFormat.CHANNEL_OUT_5POINT1;
        int samplerate = 48000;

        int minBufferSize = AudioTrack.getMinBufferSize(samplerate, channelmask, encoding);
        Log.d(TAG,"minBufferSize: " + minBufferSize);
//        int minBufferSize = 4096;

        AudioAttributes.Builder aab = new AudioAttributes.Builder();
        aab.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
        aab.setFlags(AudioAttributes.FLAG_HW_AV_SYNC);
        aab.setUsage(AudioAttributes.USAGE_MEDIA);
        AudioAttributes aa = aab.build();

        AudioFormat.Builder afb = new AudioFormat.Builder();
        afb.setEncoding(encoding);
        afb.setChannelMask(channelmask);
        afb.setSampleRate(samplerate);
        AudioFormat af = afb.build();

        AudioTrack.Builder atb = new AudioTrack.Builder();
        atb.setAudioAttributes(aa);
        atb.setAudioFormat(af);
        atb.setSessionId(mAudioSessionId);
        atb.setTransferMode(AudioTrack.MODE_STREAM);

        try {
            mAudioTrack = new android.media.AudioTrack(aa,af,minBufferSize,AudioTrack.MODE_STREAM,mAudioSessionId);
            // mAudioTrack = atb.build();
        }
        catch (IllegalArgumentException e) {
            Log.d(TAG,"AudioTrack construct() IllegalArgumentException");
            e.printStackTrace();
        }
        Log.d(TAG,"AudioTrack create done");

        try{
            mAudioTrack.play();
        }
        catch (IllegalStateException e)
        {
            Log.d(TAG,"AudioTrack.play() IllegalStateException");
            e.printStackTrace();
            return ;
        }
        Log.d(TAG,"AudioTrack play done");

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        boolean stop = false;

//            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4096);
//            ByteBuffer inputBuffer = ByteBuffer.allocate(minBufferSize);

        int index = 0;
        int readCount = 0;
        int totalReadCount = 0;
        int writeCount = 0;
        byte[] byte_array = new byte[4096];
//        ByteBuffer tempBuffer = new ByteBuffer();
        long timstamp = 0;
        while (index<939) {

//            String filename = "/sdcard/dump_eac3/frame_" + index + ".eac3";
//            String filename = "/sdcard/dump_ac4_48000_3ch/frame_" + index + ".ac4";
            String filename = "/sdcard/Channel_ID_voices_321_ddp_A/frame_" + index;

            index++;
            Log.d(TAG, "will read " + filename);
            File file = new File(filename);

            DataInputStream inputStream = null;
            try {
                inputStream = new DataInputStream(new FileInputStream(file));
            }
            catch (FileNotFoundException e)
            {
                Log.d(TAG,"DataInputStream FileNotFoundException");
                e.printStackTrace();
                return ;
            }

            try {
                readCount = inputStream.read(byte_array);
                Log.d(TAG, "read bytes " + readCount);
                inputStream.close();
            }
            catch (IOException e)
            {
                Log.d(TAG,"read IOException");
                e.printStackTrace();
                return ;
            }

//            try {
//                Thread.sleep(20);
//            } catch (InterruptedException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }

            if (readCount > 0) {
                totalReadCount += readCount;
                Log.d(TAG, "file read: " + readCount + " total: " + totalReadCount + " (" + totalReadCount / 1024 + "KB) " + index*32 + "ms");
//                public int write (ByteBuffer audioData, int sizeInBytes, int writeMode, long timestamp)
                ByteBuffer buf = ByteBuffer.wrap(byte_array);
                writeCount = mAudioTrack.write(buf,readCount,AudioTrack.WRITE_BLOCKING,timstamp);

                timstamp += 32*1000*1000;//32ms
//                writeCount = mAudioTrack.write(byte_array, 0, readCount, AudioTrack.WRITE_BLOCKING);
                Log.d(TAG, "write: " + writeCount);
            } else {
                Log.d(TAG, "file read end");
                break;
            }
        }
        Log.d(TAG, "loop quit");
    }


    void CreateMediaCodec()
    {

        String MediaVideoPath = "/sdcard/seraphic/ChID_voices_321_ddp_V.mp4";
//        String MediaVideoPath = "/sdcard/seraphic/hdr.mp4";
        File MediaFile = new File(MediaVideoPath);
        String state = Environment.getExternalStorageState(MediaFile);
        Log.d(TAG,"state: " + state);
        if(!state.equals(Environment.MEDIA_MOUNTED))
        {
            Log.e(TAG, "MediaFile not mounted");
            return ;
        }else{
            Log.d(TAG, "MediaFile mounted");
        }



        MediaExtractor mVideoExtractor = new MediaExtractor();
        try {
            Log.d(TAG, "create extractor");

            mVideoExtractor.setDataSource(MediaVideoPath);
            int numTracks = mVideoExtractor.getTrackCount();
            Log.d(TAG,"numTracks: " + numTracks);

            int video_track_index = 0;
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = mVideoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    video_track_index = i;
                    Log.d(TAG,"video track index: " + video_track_index);
                    mVideoExtractor.selectTrack(video_track_index);
                }
            }

            MediaFormat mediaFormat = mVideoExtractor.getTrackFormat(video_track_index);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(TAG, "decode video,  mime: " + mime + ", width: " + width + ", height: " + height);



//            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback, true);
            mediaFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, mAudioSessionId);

            mMediaCodec = MediaCodec.createDecoderByType(mime);
            mMediaCodec.configure(mediaFormat, mUIVideoSurface.getHolder().getSurface(), null, 0);
            mMediaCodec.start();



            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            boolean stop = false;

            while (!stop && !sawOutputEOS) {

                if (!sawInputEOS) {
                    int inputBufferId = mMediaCodec.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                    if (inputBufferId >= 0) {
                        Log.d(TAG,"input buffer available idx: " + inputBufferId);
                        ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferId);
                        int sampleSize = mVideoExtractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            mMediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                            Log.d(TAG, "sawInputEOS is true");
                        } else {
                            long pts = mVideoExtractor.getSampleTime();
                            Log.d(TAG,"queue input buffer size: " + sampleSize + " pts: " + pts);
                            mMediaCodec.queueInputBuffer(inputBufferId, 0, sampleSize, pts, 0);
                            if(!mVideoExtractor.advance())
                            {
                                Log.d(TAG, "no more sample");
                            }

//                            try {
//                                Thread.sleep(40);
//                            } catch (InterruptedException e) {
//                                // TODO Auto-generated catch block
//                                e.printStackTrace();
//                            }
                        }
                    }
                }

//                Log.d(TAG, "sleep 3 seconds");
//                try {
//                    Thread.sleep(1000*3);
//                } catch (InterruptedException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//
//
//                Log.d(TAG, "draw black begin");
//                mUIVideoSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
////                Canvas canvas = mUIVideoSurface.getHolder().lockCanvas();
//                Canvas canvas = mUIVideoSurface.getHolder().getSurface().lockCanvas(null);
//                canvas.drawColor(Color.BLUE);
////                mUIVideoSurface.getHolder().unlockCanvasAndPost(canvas);
//                mUIVideoSurface.getHolder().getSurface().unlockCanvasAndPost(canvas);
//                Log.d(TAG, "draw black end");

                // no need to dequeue explicitly output buffers. The codec
                // does this directly to the sideband layer.

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mMediaCodec != null) {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            }
            if (mVideoExtractor != null) {
                mVideoExtractor.release();
                mVideoExtractor = null;
            }
        }
    }

    public static void verifyStoragePermissions(Activity activity) {
        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,"android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}