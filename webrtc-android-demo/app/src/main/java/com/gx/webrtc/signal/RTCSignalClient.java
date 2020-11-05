package com.gx.webrtc.signal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.gx.webrtc.MyApplication;
import com.gx.webrtc.ui.ReceiveCallActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static android.content.Context.POWER_SERVICE;
import static org.java_websocket.WebSocket.READYSTATE.OPEN;

/**
 * 信令客户端
 */
public class RTCSignalClient {
    private static final String TAG = "RTCSignalClient";
    private static  final String WS_URL = "ws://127.0.0.1:8099";//信令服务器地址
    public static final String SIGNAL_TYPE_PING = "ping";//连接
    public static final String SIGNAL_TYPE_PONG = "pong";//连接
    public static final String SIGNAL_TYPE_CONNECT = "connect";//连接
    public static final String SIGNAL_TYPE_VOICE = "voice";//语音通话
    public static final String SIGNAL_TYPE_VIDEO = "video";//视频通话
    public static final String SIGNAL_TYPE_REQUEST = "request";//请求通话
    public static final String SIGNAL_TYPE_RESPONSE = "response";//响应通话
    public static final String SIGNAL_TYPE_RECEIVE = "receive";//接受
    public static final String SIGNAL_TYPE_REJECT = "reject";//拒绝
    public static final String SIGNAL_TYPE_DROPPED = "dropped";//挂断
    public static final String SIGNAL_TYPE_INCALL = "incall";//对方通话中
    public static final String SIGNAL_TYPE_OFFLINE = "offline";//对方不在线
    public static final String SIGNAL_TYPE_INVITE_SUCCEED = "invite-succeed";//邀请成功
    public static final String SIGNAL_TYPE_OFFER = "offer";//发送offer给对端peer
    public static final String SIGNAL_TYPE_ANSWER = "answer";//发送offer给对端peer
    public static final String SIGNAL_TYPE_CANDIDATE = "candidate";//发送candidate给对端peer
    private OnSignalEventListener mOnSignalEventListener;//信令回调
    private WebSocketClient mWebSocketClient = null;//webSocket
    private String mUserId;//用户账号
    private String mRoomId;//房间号
    private Context context =MyApplication.INSTANCE.getBaseContext();


    public RTCSignalClient() {
        connect();
    }

    /**
     * 连接
     */
    public void connect(){
        if (mWebSocketClient==null){
            URI uri = null;
            try {
                uri = new URI(WS_URL);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            mWebSocketClient = new JWebSocketClient(uri);   // 新建websocket类
            mWebSocketClient.connect(); // 去连接信令服务

        }
    }

    /**
     * 判断是否已连接
     * @return
     */
    public boolean isConnect(){
        if (mWebSocketClient.getReadyState()==OPEN){
            return true;
        }
        return false;
    }
    /**
     * 设置回调函数
     * @param listener
     */
    public void setOnSignalEventListener(final OnSignalEventListener listener){
        mOnSignalEventListener = listener;// 设置回调函数
        mOnSignalEventListener.onConnecting();  // 通知应用层正在连接信令服务器
    }
    public class JWebSocketClient extends WebSocketClient {
        public JWebSocketClient(URI serverUri) {
            super(serverUri, new Draft_17());
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) { // 说明websocket连接成功
            Log.i(TAG, "onOpen()");
            Log.i(TAG, "信令服务器连接成功");
            try {
                JSONObject args = new JSONObject();
                args.put("cmd", SIGNAL_TYPE_CONNECT);//连接
                args.put("uid", MyApplication.INSTANCE.getAccount());
                mWebSocketClient.send(args.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(mOnSignalEventListener != null) {
                mOnSignalEventListener.onConnected();
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onMessage(String message) { // 接收服务器发送过来的数据
            try {
                JSONObject jsonMsg = new JSONObject(message);
                String cmd = jsonMsg.getString("cmd");
                switch (cmd){
                      case SIGNAL_TYPE_INVITE_SUCCEED:
                          mRoomId=jsonMsg.getString("roomId");//房间号
                          mUserId =jsonMsg.getString("uid");//用户账号 ;
                          if (mUserId.equals(MyApplication.INSTANCE.getAccount())){//是对方账号时
                              Log.i(TAG,"成功接收邀请，弹窗通话页面");
                              turnOnScreen();//唤醒锁屏
                              Intent intent=new Intent(context, ReceiveCallActivity.class);
                              intent.putExtra("remoteUid",jsonMsg.getString("remoteUid"));//获取对方账号
                              intent.putExtra("type",jsonMsg.getString("type"));//通话类型
                              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
                              context.startActivity(intent);
                          }
                    case SIGNAL_TYPE_PING://ping
                        JSONObject args = new JSONObject();
                        args.put("cmd", SIGNAL_TYPE_PING);//ping
                        args.put("roomId", mRoomId);//ping
                        args.put("uid", mUserId);//ping
                        mWebSocketClient.send(args.toString());
                        break;
                }
                if(mOnSignalEventListener != null) {
                    switch (cmd) {
                        case SIGNAL_TYPE_INVITE_SUCCEED:
                            mOnSignalEventListener.onSucceed(jsonMsg);
                            break;
                        case SIGNAL_TYPE_RECEIVE:
                            mOnSignalEventListener.onReceive(jsonMsg);
                            break;
                        case SIGNAL_TYPE_REJECT:
                            mOnSignalEventListener.onReject(jsonMsg);
                            break;
                        case SIGNAL_TYPE_DROPPED:
                            mOnSignalEventListener.onDropped(jsonMsg);
                            break;
                        case SIGNAL_TYPE_VOICE:
                            mOnSignalEventListener.onVoice(jsonMsg);
                            break;
                        case SIGNAL_TYPE_INCALL:
                            mOnSignalEventListener.onInCall(jsonMsg);
                            break;
                        case SIGNAL_TYPE_OFFLINE:
                            mOnSignalEventListener.onOffline(jsonMsg);
                            break;
                        case SIGNAL_TYPE_OFFER:
                            mOnSignalEventListener.onOffer(jsonMsg);
                            break;
                        case SIGNAL_TYPE_ANSWER:
                            mOnSignalEventListener.onAnswer(jsonMsg);
                            break;
                        case SIGNAL_TYPE_CANDIDATE:
                            mOnSignalEventListener.onCandidate(jsonMsg);
                            break;
                        default:
                            break;
                    }
                  }
                } catch (JSONException e) {
                    Log.e(TAG, "WebSocket message JSON parsing error: " + e.toString());
                }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {//连接关闭
            Log.w(TAG, "onClose() reason:" + reason + ", clode:" +code);
            mWebSocketClient = null;
            if(mOnSignalEventListener != null) {
                mOnSignalEventListener.onClose();
            }
        }

        @Override
        public void onError(Exception ex) {
            Log.e(TAG, "onError()" + ex);
            mWebSocketClient = null;
            if(mOnSignalEventListener != null) {
                mOnSignalEventListener.onDisconnected();
            }
        }
    }

    public interface OnSignalEventListener {
        void onConnected();
        void onConnecting();
        void onDisconnected();
        void onClose();
        void onSucceed(JSONObject message);//通话成功
        void onReceive(JSONObject message);//接受
        void onReject(JSONObject message);//拒绝
        void onDropped(JSONObject message);//取消
        void onVoice(JSONObject message);//切换语音通话
        void onInCall(JSONObject message);//通话中
        void onOffline(JSONObject message);//不在线
        void onOffer(JSONObject message);//offer
        void onAnswer(JSONObject message);//answer
        void onCandidate(JSONObject message);//媒体协商
    }

    /**
     * 获取房间号
     * @return
     */
    public String getRoomId(){
        return mRoomId;
    }

    /**
     * 获取用户账号
     * @return
     */
    public String getUserId() {
        return mUserId;
    }

    /**
     * 请求通话
     * @param userId 请求方账号
     * @param remoteUid 对方账号
     */
    public void requestCall(String userId, String remoteUid, String type) {

        if (mWebSocketClient == null) {
            return;
        }
        mUserId = userId;
        mRoomId= UUID.randomUUID().toString();//随机生成房间号
        Log.i(TAG, userId+"邀请 "+ remoteUid+"进行通话，房间号为----"+mRoomId);
        try {
            JSONObject args = new JSONObject();
            args.put("cmd", SIGNAL_TYPE_REQUEST);
            args.put("uid", userId);
            args.put("roomId", mRoomId);
            args.put("remoteUid", remoteUid);
            args.put("type", type);
            mWebSocketClient.send(args.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    /**
     * 响应通话
     * @param userId 请求方账号
     * @param remoteUid 对方账号
     * @param type 类型 0--接受 1拒绝 2挂断
     */
    public void responseCall(String userId, String remoteUid, String type) {
        if (mWebSocketClient == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("cmd", SIGNAL_TYPE_RESPONSE);
            args.put("uid", userId);
            args.put("remoteUid", remoteUid);
            args.put("roomId", mRoomId);
            args.put("type",type);
            mWebSocketClient.send(args.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }
    /**
     * //发送offer SDP给对方（转发）
     * @param offer
     * @param remoteUid
     */
    public void sendOffer(String offer, String remoteUid) {
        Log.i(TAG, "send offer");
        if (mWebSocketClient == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("cmd", SIGNAL_TYPE_OFFER);
            args.put("roomId", mRoomId);
            args.put("uid", mUserId);
            args.put("remoteUid", remoteUid);
            args.put("msg", offer);
            mWebSocketClient.send(args.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * //发送answer SDP给对方(应答)
     * @param answer
     * @param remoteUid
     */
    public void sendAnswer(String answer, String remoteUid) {
        Log.i(TAG, "send answer");
        if (mWebSocketClient == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("cmd", SIGNAL_TYPE_ANSWER);
            args.put("roomId", mRoomId);
            args.put("uid", mUserId);
            args.put("remoteUid", remoteUid);
            args.put("msg", answer);
            mWebSocketClient.send(args.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送网络协商信息
     * @param candidate
     * @param remoteUid
     */
    public void sendCandidate(String candidate, String remoteUid) {
        Log.i(TAG, "send candidate");
        if (mWebSocketClient == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("cmd", SIGNAL_TYPE_CANDIDATE);
            args.put("roomId", mRoomId);
            args.put("uid", mUserId);
            args.put("remoteUid", remoteUid);
            args.put("msg", candidate);
            mWebSocketClient.send(args.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 断开连接
     */
    public void disConnect() {
        if (mWebSocketClient == null) {
            return;
        }
        mWebSocketClient.close();
        mWebSocketClient = null;
    }
    //唤醒锁屏
    private PowerManager.WakeLock mWakeLock;
    private PowerManager mPowerManager;
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("InvalidWakeLockTag")
    public void turnOnScreen() {
        // turn on screen
        try {
            mPowerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "bright");
            mWakeLock.acquire();
            mWakeLock.release();
        } catch (Exception e) {

        }
    }
}
