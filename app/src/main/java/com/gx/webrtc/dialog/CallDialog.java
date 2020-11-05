package com.gx.webrtc.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.gx.webrtc.R;
import com.gx.webrtc.signal.RTCSignalClient;
import com.gx.webrtc.ui.CallActivity;

/**
 * 通话类型弹窗
 */
public class CallDialog extends DialogFragment {
    private Activity activity;//上下文
    private Dialog dialog;//弹框
    private View view;
    private LinearLayout llVoice;//语音通话
    private LinearLayout llVideo;//视频通话
    private LinearLayout llCancel;//取消
    private String peerAccount;//对方账号

    public CallDialog(@NonNull Activity activity, String peerAccount) {
        this.activity=activity;
        this.peerAccount=peerAccount;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        dialog=new Dialog(activity,R.style.dialog);
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);//软键盘就会把dialog弹起，有的手机则会遮住dialog布局。
        view = View.inflate(getActivity(), R.layout.dialog_logout,null);
        dialog.setContentView(view);
        llVoice=view.findViewById(R.id.ll_voice);
        llVideo=view.findViewById(R.id.ll_video);
        llCancel=view.findViewById(R.id.ll_cancel);
        // 设置宽度为屏宽, 靠近屏幕底部。
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.BOTTOM; // 紧贴底部
        lp.alpha = 1;
        lp.dimAmount = 0.5f;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT; // 宽度持平
        lp.windowAnimations=R.style.dialog_bottom_top;//设置弹窗动画
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        setViewListener();
        return dialog;
    }

    /**
     * 事件监听
     */
    private void setViewListener(){
        //语音通话
        llVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(activity, CallActivity.class);
                intent.putExtra("remoteUid",peerAccount);
                intent.putExtra("type", RTCSignalClient.SIGNAL_TYPE_VOICE);//语音通话
                activity.startActivity(intent);
                dismiss();
            }
        });
        //视频通话
        llVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(activity,CallActivity.class);
                intent.putExtra("remoteUid",peerAccount);
                intent.putExtra("type",RTCSignalClient.SIGNAL_TYPE_VIDEO);//视频通话
                activity.startActivity(intent);
                dismiss();
            }
        });
        //取消
        llCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }
}
