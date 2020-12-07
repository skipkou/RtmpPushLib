package com.skipkou.mylib;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.skipkou.rtmplib.rtmp.OnConntionListener;
import com.skipkou.rtmplib.rtmp.RtmpHelper;
import com.skipkou.rtmplib.rtmp.encoder.AudioEncode;
import com.skipkou.rtmplib.rtmp.encoder.BaseAudioPushEncoder;

public class MainActivity extends AppCompatActivity implements OnConntionListener,
        ActivityCompat.OnRequestPermissionsResultCallback, BaseAudioPushEncoder.OnMediaInfoListener {

    protected static final int PERMISSION_REQUEST_AUDIOREC = 1;
    private final String TAG = "MainActivity";

    private EditText mEditText;
    private Button mButton;
    private String url;
    private RtmpHelper rtmpHelper;

    private boolean isStart;
    private AudioEncode pushEncode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEditText = findViewById(R.id.main_et);
        mButton = findViewById(R.id.main_btn);

        mEditText.setText("rtmp://192.168.2.20:1935/skipkou");
        mButton.setOnClickListener(v -> {
            if (!isStart) {
                url = mEditText.getText().toString();
                if (TextUtils.isEmpty(url)) {
                    url = "rtmp://192.168.2.20:1935/skipkou";
                }
                startWithPermCheck();
                mButton.setText("停止推流");
            } else {
                stopPush();
                mButton.setText("开启推流");
            }
        });
    }

    @Override
    public void onAudioInfo(byte[] data) {
        if (rtmpHelper == null) return;
        rtmpHelper.pushAudioData(data);
    }


    private void startRecord() {
        rtmpHelper = new RtmpHelper();
        rtmpHelper.setOnConntionListener(this);
        rtmpHelper.initLivePush(url);
    }

    private void startPush() {
        isStart = true;
        pushEncode = new AudioEncode(this);
        pushEncode.initEncoder(44100, 1, 8, 48000);
        pushEncode.setOnMediaInfoListener(this);
        pushEncode.start();
    }

    private void stopPush() {
        isStart = true;
        if (pushEncode != null) {
            pushEncode.stop();
            pushEncode = null;
        }

        if (rtmpHelper != null) {
            rtmpHelper.stop();
            rtmpHelper = null;
        }
        isStart = false;
    }


    @Override
    public void onConntecting() {
        Log.i(TAG, "连接中...");
    }

    @Override
    public void onConntectSuccess() {
        runOnUiThread(() -> Toast.makeText(this, "连接服务器成功，开始推流", Toast.LENGTH_SHORT).show());
        startPush();
    }

    @Override
    public void onConntectFail(String msg) {
        runOnUiThread(() -> Toast.makeText(this, "连接失败" + msg, Toast.LENGTH_SHORT).show());
        Log.e(TAG, "连接失败" + msg);
    }


    private void startWithPermCheck() {
        int audioPerm = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (audioPerm != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.e(TAG, "No AudioRecord permission, please check");
                Toast.makeText(this, "No AudioRecord permission, please check",
                        Toast.LENGTH_LONG).show();
            } else {
                String[] permissions = {Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE};
                ActivityCompat.requestPermissions(this, permissions,
                        PERMISSION_REQUEST_AUDIOREC);
            }
        } else {
            startRecord();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_AUDIOREC: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecord();
                } else {
                    Log.e(TAG, "No AudioRecord permission");
                    Toast.makeText(this, "No AudioRecord permission",
                            Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }
}