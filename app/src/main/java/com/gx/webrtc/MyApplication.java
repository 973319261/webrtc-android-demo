package com.gx.webrtc;

import android.app.Application;

import com.gx.webrtc.signal.RTCSignalClient;

public class MyApplication extends Application {
    public static MyApplication INSTANCE;
    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE=this;
    }
    //账号
    private String account;
    public void setAccount(String account){
        this.account=account;
    }
    public String getAccount(){
        return account;
    }
    //信令服务器
    private RTCSignalClient rtcSignalClient;
    public void setRtcSignalClient(RTCSignalClient rtcSignalClient){
        this.rtcSignalClient=rtcSignalClient;
    }
    public RTCSignalClient getRtcSignalClient(){
        return rtcSignalClient;
    }
}
