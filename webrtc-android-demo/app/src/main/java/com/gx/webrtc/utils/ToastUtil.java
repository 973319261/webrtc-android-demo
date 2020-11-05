package com.gx.webrtc.utils;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * 提示工具类
 */
public class ToastUtil {
	/** default **/
	public static void defaultToast(Context context, String txt) {
		Toast toast = Toast.makeText(context, txt, Toast.LENGTH_SHORT);
		toast.show();
	}

	public static void centerToast(Context context, String txt) {
		Toast toast = Toast.makeText(context, txt, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}
	
	public static void rightToast(Context context, String txt) {
		Toast toast = Toast.makeText(context, txt, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.RIGHT, 0, 0);
		toast.show();
	}
	private static ArrayList<Toast> toastList = new ArrayList<Toast>();

	public static void newToast(Context context, String content) {
		cancelAll();
		Toast toast = Toast.makeText(context,content, Toast.LENGTH_SHORT);
		toastList.add(toast);
		toast.show();
	}

	public static void cancelAll() {
		if (!toastList.isEmpty()){
			for (Toast t : toastList) {
				t.cancel();
			}
			toastList.clear();
		}
	}
}
