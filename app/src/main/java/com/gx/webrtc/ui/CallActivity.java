package com.gx.webrtc.ui;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gx.webrtc.MyApplication;
import com.gx.webrtc.R;
import com.gx.webrtc.signal.MyIceServer;
import com.gx.webrtc.signal.RTCSignalClient;
import com.gx.webrtc.utils.ToastUtil;
import com.gx.webrtc.utils.Tools;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 邀请通话页面
 */
public class CallActivity extends AppCompatActivity {

    private static final int VIDEO_RESOLUTION_WIDTH = 640;
    private static final int VIDEO_RESOLUTION_HEIGHT = 480;
    private static final int VIDEO_FPS = 15;
    private Activity activity;
    private SurfaceViewRenderer mLocalSurfaceView;//本地视频窗口
    private SurfaceViewRenderer mRemoteSurfaceView;//远程视频窗口
    private TextView tvCallName;//昵称
    private TextView tvCallState;//通话状态
    private TextView tvCallTime;//通话时长
    private LinearLayout llDescribe;//描述信息
    private LinearLayout llCancel;//取消
    private LinearLayout llVoice;//切换语音
    private LinearLayout llCamera;//转换摄像头
    private LinearLayout llMute;//禁麦
    private LinearLayout llMuteWhite;//禁麦
    private LinearLayout llMuteBlack;//禁麦
    private LinearLayout llHands;//免提
    private LinearLayout llHandsWhite;//免提
    private LinearLayout llHandsBlack;//免提
    private LinearLayout llAwait;//等待中按钮
    private LinearLayout llCall;//通话中按钮
    private String userId;//己方账号
    private String remoteUid;//对方账号
    private Boolean isVoice;//是否语音通话
    private MediaPlayer mediaPlayer;//提示音
    private boolean isCallSuccess=false;//通话是否成功



    private static final String TAG = "CallActivity";

    //**************************************各种约束******************************************/
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";//回音
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";//自动增益控制通常是麦克风提供的功能
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";//高通滤波
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";//噪音

    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String LOCAL_STREAM_ID = "ARDAMS";

    private EglBase mRootEglBase;//渲染API（相机）和本地窗口系统之间的接口（渲染视频）

    private PeerConnection mPeerConnection; // 通讯对象
    private PeerConnectionFactory mPeerConnectionFactory;//用来生成PeerConnection的工厂类
    private MediaStream mLocalStream = null;//媒体数据流

    private SurfaceTextureHelper mSurfaceTextureHelper;
    private ProxyVideoSink localVideoSink;//代理本地视频渲染器
    private ProxyVideoSink remoteVideoSink;//代理远程视频渲染器
    private VideoTrack localVideoTrack;//本地视频轨迹
    private AudioTrack localAudioTrack;//本地音频轨迹

    private CameraVideoCapturer mCameraVideoCapturer;//操作视频设备（调取摄像头）
    private int currVolume;//当前音量
    private CountDownTimer countDownTimer;//通话定时器
    private Timer timer = new Timer();//连接定时器
    private long startTime = 0;//初始时间
    private ExecutorService mExecutor;//单线程化的线程池
    private String mRoomId;
    private ArrayList<PeerConnection.IceServer> ICEServers;
    // turn and stun
    //turn 服务器IP
    private static MyIceServer[] iceServers = {
        new MyIceServer("stun:47.99.53.217:3478"),
        new MyIceServer("turn:47.99.53.217:3478?transport=udp",
                "koi",
                "123456"),
        new MyIceServer("turn:47.99.53.217:3478?transport=tcp",
                "koi",
                "123456")
    };

    private AudioManager mAudioManager;//音频管理器
    private RTCSignalClient mSignalClient = MyApplication.INSTANCE.getRtcSignalClient();//信号客户端
    //WebRTC支持将视频和音频放入MediaStream的方式。
    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        return audioConstraints;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity=this;
        setContentView(R.layout.activity_call);
        mLocalSurfaceView = findViewById(R.id.LocalSurfaceView);
        mRemoteSurfaceView = findViewById(R.id.RemoteSurfaceView);
        tvCallName=findViewById(R.id.tv_call_name);
        tvCallState=findViewById(R.id.tv_call_state);
        tvCallTime=findViewById(R.id.tv_call_time);
        llDescribe=findViewById(R.id.ll_describe);
        llCancel=findViewById(R.id.ll_call_cancel);
        llCamera=findViewById(R.id.ll_call_camera);
        llVoice=findViewById(R.id.ll_call_voice);
        llMute=findViewById(R.id.ll_call_mute);
        llMuteWhite=findViewById(R.id.ll_call_mute_white);
        llMuteBlack=findViewById(R.id.ll_call_mute_black);
        llHands=findViewById(R.id.ll_call_hands);
        llHandsWhite=findViewById(R.id.ll_call_hands_white);
        llHandsBlack=findViewById(R.id.ll_call_hands_black);
        llAwait=findViewById(R.id.ll_call_await);
        llCall=findViewById(R.id.ll_call);
        userId=MyApplication.INSTANCE.getAccount();//获取该用户账号
        remoteUid=getIntent().getStringExtra("remoteUid");//获取对方账号
        mRoomId=mSignalClient.getRoomId();//获取房间号
        tvCallName.setText(remoteUid);//设置昵称
        String type=getIntent().getStringExtra("type");//通话类型
        isVoice=type.equals(RTCSignalClient.SIGNAL_TYPE_VOICE)?true:false;//判断通话类型 true 语音通话 false 视频通话
        tvCallState.setText("正在等待对方接受邀请...");
        mSignalClient.setOnSignalEventListener(mOnSignalEventListener);//设置回调函数
        mSignalClient.requestCall(userId,remoteUid,type);//请求对方通话
        mExecutor = Executors.newSingleThreadExecutor();//单线程化的线程池
        //音频管理器
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        currVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);//获取当前音量
        mAudioManager.setSpeakerphoneOn(true);//设置是否打开扩音器
        mAudioManager.setMicrophoneMute(false);//设置是否让麦克风静音
        updateSpeakerphoneOnState();//改变免提状态
        //创建相机API
        mRootEglBase = EglBase.create();
        //本地视频
        mLocalSurfaceView.init(mRootEglBase.getEglBaseContext(), null);//初始化渲染画面
        mLocalSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);//设置图像的拉伸比例（画面铺满容器）
        mLocalSurfaceView.setMirror(true);//设置图像显示时反转，不然视频显示的内容与实际内容正好相反。
        mLocalSurfaceView.setEnableHardwareScaler(false /* enabled */);//是否打开便件进行拉伸。
        //远端视频
        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);//初始化渲染画面
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);//设置图像的拉伸比例（画面铺满容器）
        mRemoteSurfaceView.setMirror(true);//设置图像显示时反转，不然视频显示的内容与实际内容正好相反。
        mRemoteSurfaceView.setEnableHardwareScaler(true /* enabled */);//是否打开便件进行拉伸。
        mRemoteSurfaceView.setZOrderMediaOverlay(true);
        //代理本地视频渲染器
        localVideoSink = new ProxyVideoSink();//实例化
        localVideoSink.setTarget(mLocalSurfaceView);//把获取到的摄像头视频流设置到大窗口
        //创建工厂
        mPeerConnectionFactory = createPeerConnectionFactory(this);
        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
        //创建（视频设备）VideoCapturer（调取摄像头）
        mCameraVideoCapturer = createVideoCapturer();
        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);//参数说明是否为屏幕录制采集（采集视频数据源）
        mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
        //初始化
        mCameraVideoCapturer.initialize(mSurfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        //视频采集
        localVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);//开启视频轨迹
        localVideoTrack.addSink(localVideoSink); //将从摄像头采集的数据设置到视频轨迹
        //音频采集
		AudioSource audioSource = mPeerConnectionFactory.createAudioSource(createAudioConstraints());
        localAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);//开启音频轨迹
        //创建本地媒体流
        mLocalStream = mPeerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        // 本地流从mAudioTrack + mVideoTrack
        mLocalStream.addTrack(localAudioTrack);//把本地音频轨迹添加到本地流
        mLocalStream.addTrack(localVideoTrack);//把本地视频轨迹添加到本地流
        updateCallState(false,isVoice);//视频通话连接中
        setViewListener();//监听事件
    }

    /**
     * 监听事件
     */
    private void setViewListener() {
        //取消通话
        llCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close("通话结束");//关闭通话页面
                if (isCallSuccess){
                    mSignalClient.responseCall(userId,remoteUid,RTCSignalClient.SIGNAL_TYPE_DROPPED);//通知对方已取消通话
                }
            }
        });
        //切换语音通话
        llVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvCallState.setText("切换语音通话中...");
                updateCallState(true,true);//切换到语音通话
                mSignalClient.responseCall(userId,remoteUid,RTCSignalClient.SIGNAL_TYPE_VOICE);//通知对方切换语音通话
            }
        });
        //切换摄像头
        llCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraVideoCapturer.switchCamera(null);
            }
        });
        //禁麦
        llMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (localAudioTrack.enabled()){//开启
                    localAudioTrack.setEnabled(false);//关闭禁麦
                    llMuteWhite.setVisibility(View.GONE);
                    llMuteBlack.setVisibility(View.VISIBLE);
                    ToastUtil.newToast(activity,"麦克风已关闭");
                }else {
                    localAudioTrack.setEnabled(true);//开启禁麦
                    llMuteWhite.setVisibility(View.VISIBLE);
                    llMuteBlack.setVisibility(View.GONE);
                    ToastUtil.newToast(activity,"麦克风已打开");
                }
            }
        });
        //免提
        llHands.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mAudioManager.isSpeakerphoneOn()) {//已经打开扩音器
                    mAudioManager.setSpeakerphoneOn(false);//关闭扩音器
                    mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,currVolume,
                            AudioManager.STREAM_VOICE_CALL);//还原原来的声音
                    llHandsWhite.setVisibility(View.VISIBLE);
                    llHandsBlack.setVisibility(View.GONE);
                    ToastUtil.newToast(activity,"扬声器已关闭");
                }else {//免提
                    mAudioManager.setSpeakerphoneOn(true);//打开扩音器
                    mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                            mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                            AudioManager.STREAM_VOICE_CALL);
                    llHandsWhite.setVisibility(View.GONE);
                    llHandsBlack.setVisibility(View.VISIBLE);
                    ToastUtil.newToast(activity,"扬声器已打开");
                }
            }
        });
        // 监听音频播放完的代码，实现音频的自动循环播放
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer arg0) {
                mediaPlayer.start();
                mediaPlayer.setLooping(true);
            }
        });
        //小窗口切换
        mRemoteSurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               /* if (isLocal){//小窗口里面是本地视频时

                }
                localVideoSink.setTarget(mRemoteSurfaceView);//把本地视频设置到小窗口
                remoteVideoSink.setTarget(mLocalSurfaceView);//把远程视频设置到大窗口*/
            }
        });
    }

    /**
     * 获取摄像头的视频流
     */
    public static class ProxyVideoSink implements VideoSink {
        private VideoSink mTarget;
        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (mTarget == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }
            mTarget.onFrame(frame);
        }
        synchronized void setTarget(VideoSink target) {
            this.mTarget = target;
        }
    }

    public static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "SdpObserver: onCreateSuccess !");
        }

        @Override
        public void onSetSuccess() {
            Log.i(TAG, "SdpObserver: onSetSuccess");
        }

        @Override
        public void onCreateFailure(String msg) {
            Log.e(TAG, "SdpObserver onCreateFailure: " + msg);
        }

        @Override
        public void onSetFailure(String msg) {
            Log.e(TAG, "SdpObserver onSetFailure: " + msg);
        }
    }
    /**
     * 更改通话状态
     * @param isCall 是否已通话成功
     * @param isVoice 是否语音通话
     */
    private void updateCallState(final boolean isCall, final boolean isVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isCall) {//通话连接成功
                    if (mediaPlayer.isPlaying()){
                        mediaPlayer.stop();//关闭提示音
                    }
                    llCall.setVisibility(View.VISIBLE);//显示通话中按钮
                    llCancel.setVisibility(View.VISIBLE);//显示取消按钮
                    tvCallTime.setVisibility(View.VISIBLE);//显示通话时间
                    tvCallState.setText("正在通话中...");
                    if (isVoice){//语音通话
                        llDescribe.setVisibility(View.VISIBLE);//显示描述信息
                        llMute.setVisibility(View.VISIBLE);//显示禁麦按钮
                        llHands.setVisibility(View.VISIBLE);//显示免提按钮
                        llVoice.setVisibility(View.GONE);//隐藏切换语音按钮
                        llCamera.setVisibility(View.GONE);//隐藏切换摄像头按钮
                        mLocalSurfaceView.setVisibility(View.GONE);//隐藏远程视频窗口
                        mRemoteSurfaceView.setVisibility(View.GONE);//隐藏远程视频窗口
                        localVideoTrack.setEnabled(false);//关闭视频轨迹
                        mCameraVideoCapturer.dispose();//销毁画面
                        mAudioManager.setSpeakerphoneOn(false);//设置是否打开扩音器
                        updateSpeakerphoneOnState();//改变免提状态
                    }else {//视频通话
                        llDescribe.setVisibility(View.GONE);//隐藏描述信息
                        llVoice.setVisibility(View.VISIBLE);//显示切换语音按钮
                        llCamera.setVisibility(View.VISIBLE);//显示切换摄像头按钮
                        llMute.setVisibility(View.GONE);//隐藏禁麦按钮
                        llHands.setVisibility(View.GONE);//隐藏免提按钮
                        mLocalSurfaceView.setVisibility(View.VISIBLE);//显示远程视频窗口
                        mRemoteSurfaceView.setVisibility(View.VISIBLE);//显示远程视频窗口
                    }
                    ToastUtil.newToast(activity,"连接成功");
                } else {//通话未连接
                    llDescribe.setVisibility(View.VISIBLE);//显示描述信息
                    llAwait.setVisibility(View.GONE);//隐藏等待按钮
                    llCall.setVisibility(View.VISIBLE);//显示通话中按钮
                    llCancel.setVisibility(View.VISIBLE);//显示取消按钮
                    if (isVoice) {//语音通话
                        llMute.setVisibility(View.VISIBLE);//显示禁麦按钮
                        llHands.setVisibility(View.VISIBLE);//显示免提按钮
                    }
                    llVoice.setVisibility(View.GONE);//隐藏切换语音按钮
                    llCamera.setVisibility(View.GONE);//隐藏切换摄像头按钮

                    mRemoteSurfaceView.setVisibility(View.GONE);//隐藏远程视频窗口
                    if (isVoice){//语音通话
                        mLocalSurfaceView.setVisibility(View.GONE);//隐藏本地视频窗口
                    }
                    //初始化通话时长定时器
                    countDownTimer = new CountDownTimer(24*60*60*1000,1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            String content = Tools.showTimeCount(System.currentTimeMillis() - startTime);
                            tvCallTime.setText(content);
                        }

                        @Override
                        public void onFinish() {

                        }
                    };
                    mediaPlayer = MediaPlayer.create(activity,
                            R.raw.warning_tone);
                    mediaPlayer.start();//开始提示音
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            close("通话结束");//关闭通话页面
                            mSignalClient.responseCall(userId,remoteUid,RTCSignalClient.SIGNAL_TYPE_DROPPED);//通知对方已取消通话
                        }
                    },30000);//30s对方无反应便结束通话
                }
            }
        });
    }

    /**
     * 判断是否打开扩音器
     */
    private void updateSpeakerphoneOnState(){
        if(mAudioManager.isSpeakerphoneOn()) {//已经打开扩音器
            llHandsWhite.setVisibility(View.GONE);
            llHandsBlack.setVisibility(View.VISIBLE);
        }else {//免提
            llHandsWhite.setVisibility(View.VISIBLE);
            llHandsBlack.setVisibility(View.GONE);
        }
    }

    /**
     * 开始通话（媒体协商、网络协商）
     */
    public void doStartCall() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvCallState.setText("正在连接中...");
            }
        });
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }
        //媒体约束
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));// 告诉另一端，你是否想接收音频，默认true
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));// 告诉另一端，你是否想接收视频，默认true
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));//加密
        //createOffer
        mPeerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.i(TAG, "Create local offer Success: \n" + sessionDescription.description);
                //设置setLocalDescription
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                Log.i(TAG, "SetLocalDescription Success");
                //发送offer SDP给对方
                mSignalClient.sendOffer(toJsonSessionDescription(sessionDescription), remoteUid);

            }
        }, mediaConstraints);
    }


    /**
     * 创建通讯对象
     * Create PeerConnection
     * @return
     */
    public PeerConnection createPeerConnection() {
        Log.i(TAG, "Create PeerConnection ...");
        this.ICEServers = new ArrayList<>();
        //coturn的stun和turn的地址
        if (iceServers != null) {
            for (MyIceServer myIceServer : iceServers) {
                PeerConnection.IceServer iceServer = PeerConnection.IceServer
                        .builder(myIceServer.uri)
                        .setUsername(myIceServer.username)
                        .setPassword(myIceServer.password)
                        .createIceServer();
                ICEServers.add(iceServer);
            }
        }
        //PeerConnection配置
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(ICEServers); // 传入coturn的stun和turn的地址
        configuration.iceTransportsType = PeerConnection.IceTransportsType.RELAY;   // 设置为中继模式
        //创建通讯对象PeerConnection
        PeerConnection connection = mPeerConnectionFactory.createPeerConnection(configuration, mPeerConnectionObserver);
        if (connection == null) {
            Log.e(TAG, "Failed to createPeerConnection !");
            return null;
        }
        //添加本地媒体流到PeerConnection中
        connection.addStream(mLocalStream);
        return connection;
    }

    /**
     * 通讯对象工厂类
     * @param context
     * @return
     */
    public PeerConnectionFactory createPeerConnectionFactory(Context context) {
        final VideoEncoderFactory encoderFactory;//视频加密
        final VideoDecoderFactory decoderFactory;//视频解码
        //视频加密
        encoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(), false /* enableIntelVp8Encoder */, true);
        //视频解码
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());
        //初始化工厂类
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions());
        //设置参数
        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory);
        builder.setOptions(null);
        return builder.createPeerConnectionFactory();
    }

    /**
     *
     * @return
     */
    //初始化VideoCapturer（操作视频设备,例如摄像头）
    private CameraVideoCapturer createVideoCapturer() {
        //判断是否支持
        if (Camera2Enumerator.isSupported(this)) {
            return createCameraCapturer(new Camera2Enumerator(this));
        } else {
            return createCameraCapturer(new Camera1Enumerator(true));
        }
    }

    /**
     * 采集视频数据
     * @param enumerator
     * @return
     */
    private CameraVideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    /**
     * 观察者回调（等待SDP交换完便触发onIceCandidate进行网络协商）
     */
    private PeerConnection.Observer mPeerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {//在 SignalingState 更改时触发。
            Log.i(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {//在 IceConnectionState 更改时触发。
            Log.i(TAG, "onIceConnectionChange: " + iceConnectionState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {//当 ICE 连接接收状态改变时触发。
            Log.i(TAG, "onIceConnectionChange: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {//当 IceGatheringState 改变时触发。
            Log.i(TAG, "onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {//当一个新的 IceCandidate 被发现时触发。
            Log.i(TAG, "onIceCandidate: " + iceCandidate);
            //发送网络协商信息给对方
            mSignalClient.sendCandidate(toJsonCandidate(iceCandidate), remoteUid);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {// 当一些 IceCandidate被移除时触发。
            for (int i = 0; i < iceCandidates.length; i++) {
                Log.i(TAG, "onIceCandidatesRemoved: " + iceCandidates[i]);
            }
            mPeerConnection.removeIceCandidates(iceCandidates);
        }
        @Override
        public void onAddStream(MediaStream mediaStream) {//当从远程的流发布时触发。
            Log.i(TAG, "onAddStream: " + mediaStream.videoTracks.size());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {//当远程的流移除时触发
            Log.i(TAG, "onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {//当远程打开 DataChannel 时触发。
            Log.i(TAG, "onDataChannel");
        }

        @Override
        public void onRenegotiationNeeded() {//当需要重新协商时触发。
            Log.i(TAG, "onRenegotiationNeeded");
        }
        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {// 当远程端发出新的 Track 时触发, 这是 setRemoteDescription 回调的结果
            if (isVoice){
                mCameraVideoCapturer.dispose();//关闭摄像头
            }
            MediaStreamTrack track = rtpReceiver.track();
            if (track instanceof VideoTrack) {  // 判别是不是VideoTrack
                Log.i(TAG, "onAddVideoTrack");
                VideoTrack remoteVideoTrack = (VideoTrack) track;//远端音频轨迹
                remoteVideoTrack.setEnabled(true);
                remoteVideoSink = new ProxyVideoSink();
                localVideoSink.setTarget(mRemoteSurfaceView);//把本地视频设置到小窗口
                remoteVideoSink.setTarget(mLocalSurfaceView);//把远程视频设置到大窗口
                remoteVideoTrack.addSink(remoteVideoSink);
            }
        }
    };
    /**
     * 信令服务器回调函数
     */
    private RTCSignalClient.OnSignalEventListener mOnSignalEventListener = new RTCSignalClient.OnSignalEventListener() {
        @Override
        public void onConnected() {
            Log.i(TAG,"Signal Server Connected !");
        }

        @Override
        public void onConnecting() {
            Log.i(TAG,"Signal Server Connecting !");
        }

        @Override
        public void onDisconnected() {
            Log.i(TAG,"Signal Server Connecting !");
        }

        @Override
        public void onClose() {
            Log.i(TAG,"Signal Server close");
            if (isCallSuccess){
                close("通话结束");//关闭通话页面
            }else {
                close("");//关闭通话页面
            }

        }

        @Override
        public void onSucceed(JSONObject message) {
            isCallSuccess=true;//通话成功
        }

        @Override
        public void onReceive(JSONObject message) {//对方接受通话
            mExecutor.execute(() -> {
                doStartCall();//开始交换SDP
            });
        }

        @Override
        public void onReject(JSONObject message) {//对方拒绝通话
            close("对方拒绝通话");//关闭通话页面
        }

        @Override
        public void onDropped(JSONObject message) {//对方取消通话
            close("对方取消通话");//关闭通话页面
        }

        @Override
        public void onVoice(JSONObject message) {//切换语音通话
            updateCallState(true,true);//切换到语音通话
        }

        @Override
        public void onInCall(JSONObject message) {//对方通话中
            close("对方通话中");//关闭通话页面
        }

        @Override
        public void onOffline(JSONObject message) {//对方不在线
            close("对方不在线");//关闭通话页面
        }

        @Override
        public void onOffer(JSONObject message) {//对方转发Offer

        }

        @Override
        public void onAnswer(JSONObject message) {//对方应答Answer
            Log.i(TAG,"Receive Remote Answer ...");
            try {
                JSONObject sdpJson = new JSONObject(message.getString("msg"));
                String description = sdpJson.getString("sdp");
                mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, description));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCandidate(JSONObject message) {//网络协商
            Log.i(TAG,"Receive Remote Candidate ...");
            try {
                String candidateString = message.getString("msg");
                IceCandidate iceCandidate = toJavaCandidate(new JSONObject(candidateString));
                mPeerConnection.addIceCandidate(iceCandidate);//添加网络协商信息
                updateCallState(true,isVoice);//通话连接成功
                if (startTime==0){
                    startTime= System.currentTimeMillis();
                }
                countDownTimer.start();//开始计时
                timer.cancel();//取消
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * 媒体协商信息 SessionDescription
     * 转换json格式
     * @param sdp
     * @return
     */
    private String toJsonSessionDescription(SessionDescription sdp) {
        Log.i(TAG, "toJsonSessionDescription");
        JSONObject json = new JSONObject();

        String type = "offer";
        if (sdp.type == SessionDescription.Type.OFFER) {
            type = "offer";
        } else if (sdp.type == SessionDescription.Type.ANSWER){
            type = "answer";
        } else if (sdp.type == SessionDescription.Type.PRANSWER){
            type = "pranswer";
        } else {
            type = "unkown";
            Log.e(TAG, "toJsonSessionDescription failed: unknown the sdp type");
        }
        String sdpDescription = sdp.description;
        try {
            json.put("sdp", sdpDescription);
            json.put("type", type);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return json.toString();
    }

    /**
     * 网络协商信息 Candidate
     * 转换json格式
     * @param candidate
     * @return
     */
    private String toJsonCandidate(IceCandidate candidate) {
        JSONObject json = new JSONObject();

        try {
            json.put("id", candidate.sdpMid);
            json.put("label", candidate.sdpMLineIndex);
            json.put("candidate", candidate.sdp);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return json.toString();
    }

    /**
     * 转换媒体信息 Candidate
     * 转换java格式
     * @param json
     * @return
     * @throws JSONException
     */
    private IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }
    /**
     * 关闭资源
     */
    public void close (String content) {
       runOnUiThread(new Runnable() {
           @Override
           public void run() {
               if (!"".equals(content)){
                   ToastUtil.newToast(activity,content);
               }
               if (countDownTimer!=null){
                   countDownTimer.cancel();//取消通话时长计算
               }
               if (timer!=null){
                   timer.cancel();//取消
               }
               if (mediaPlayer.isPlaying()){
                   mediaPlayer.stop();//关闭提示音
               }
               mLocalSurfaceView.release();
               mRemoteSurfaceView.release();
               mCameraVideoCapturer.dispose();//关闭摄像头
               if (mSurfaceTextureHelper.isTextureInUse()){
                   mSurfaceTextureHelper.dispose();
               }
               PeerConnectionFactory.stopInternalTracingCapture();
               PeerConnectionFactory.shutdownInternalTracer();
               if (mPeerConnection == null) {
                   finish();
                   return;
               }
               mPeerConnection.close();
               mPeerConnection = null;
               finish();
           }
       });
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (isVoice){//语音通话
            mCameraVideoCapturer.dispose();//关闭摄像头
        }else {//视频通话
            mCameraVideoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS);//加载摄像头
        }
    }

    @Override
    public void onBackPressed() {
        ToastUtil.newToast(activity,"正在通话中");
    }
}
