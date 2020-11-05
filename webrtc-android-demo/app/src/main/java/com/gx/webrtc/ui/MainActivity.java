package com.gx.webrtc.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.gx.webrtc.MyApplication;
import com.gx.webrtc.R;
import com.gx.webrtc.dialog.CallDialog;
import com.gx.webrtc.signal.RTCSignalClient;
import com.gx.webrtc.utils.ToastUtil;

import java.util.Timer;
import java.util.TimerTask;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    private Activity activity;
    private EditText etAccount;//账号
    private EditText etPeerAccount;//对方账号
    private Button btnSave;//保存
    private Button btnCall;//呼叫
    private RTCSignalClient signalClient;//信令客户端
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        activity=this;
        etAccount=findViewById(R.id.et_account);
        etPeerAccount=findViewById(R.id.et_peer_account);
        btnSave=findViewById(R.id.btn_save);
        btnCall=findViewById(R.id.btn_call);
        //保存事件
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account=etAccount.getText().toString();
                if ("".equals(account)){
                    ToastUtil.newToast(activity,"请输入账号");
                    return;
                }
                MyApplication.INSTANCE.setAccount(account);//保存账号到内存中
                signalClient = new RTCSignalClient(); // 创建信令服务器（音视频通话）
                signalClient.connect();//连接信令服务器
                MyApplication.INSTANCE.setRtcSignalClient(signalClient);//保存到内存
                ProgressDialog progressDialog=new ProgressDialog(activity);
                progressDialog.show();
                Timer timer = new Timer();//连接定时器
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                      runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                              if (signalClient.isConnect()){//判断是否连接成功
                                  ToastUtil.newToast(activity,"服务器连接成功");
                                  etAccount.setEnabled(false);//禁止输入
                                  btnSave.setEnabled(false);//禁止按钮
                                  btnSave.setText("服务器连接成功");
                              }else {
                                  ToastUtil.newToast(activity,"服务器连接失败");
                              }
                              progressDialog.dismiss();
                          }
                      });
                    }
                },3000);//3s后判断是否连接成功
            }
        });
        //呼叫事件
        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account=MyApplication.INSTANCE.getAccount();
                String peerAccount=etPeerAccount.getText().toString();
                if ("".equals(account)){
                    ToastUtil.newToast(activity,"请输入账号");
                    return;
                }
                if ("".equals(peerAccount)){
                    ToastUtil.newToast(activity,"请输入对方账号");
                    return;
                }
                if (peerAccount.equals(account)){
                    ToastUtil.newToast(activity,"不能与自己通话");
                    return;
                }
                if (signalClient!=null && signalClient.isConnect() ){//判断是否连接成功
                    //显示通话类型弹窗
                    CallDialog callDialog=new CallDialog(activity,peerAccount);
                    callDialog.show(getSupportFragmentManager(),"");
                }else {
                    ToastUtil.newToast(activity,"服务器未连接");
                }
            }
        });
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, "Need permissions for camera & microphone", 0, perms);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        signalClient.disConnect();
    }
}
