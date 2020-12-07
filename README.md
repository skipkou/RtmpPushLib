# RtmpPushLib

[![](https://jitpack.io/v/skipkou/RtmpPushLib.svg)](https://jitpack.io/#skipkou/RtmpPushLib)

rtmp推流纯音频

使用方法
Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
Step 2. Add the dependency

	dependencies {
	        implementation 'com.github.skipkou:RtmpPushLib:1.0.0'
	}

初始化rtmpHelper

        rtmpHelper = new RtmpHelper();
        rtmpHelper.setOnConntionListener(this);
        rtmpHelper.initLivePush(url);
        
在连接成功后

    @Override
    public void onConntectSuccess() {
        runOnUiThread(() -> Toast.makeText(this, "连接服务器成功，开始推流", Toast.LENGTH_SHORT).show());
        startPush();
    }
    
初始化AudioEncoder

      private void startPush() {
        isStart = true;
        pushEncode = new AudioEncode(this);
        pushEncode.initEncoder(44100, 1, 8, 48000);
        pushEncode.setOnMediaInfoListener(this);
        pushEncode.start();
    }
        
监听onAudioInfo上送数据

    @Override
    public void onAudioInfo(byte[] data) {
        if (rtmpHelper == null) return;
        rtmpHelper.pushAudioData(data);
    }

        
        
