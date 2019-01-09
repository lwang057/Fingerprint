package com.lwang.fingerprint;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AlertDialog;

import com.lwang.fingerprint.core.FingerprintCore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FingerprintCore mFingerprintCore;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.fingerprint_recognition_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFingerprintRecognition();
            }
        });

        getFingerprintInfo();
    }


    @TargetApi(Build.VERSION_CODES.M)
    public void getFingerprintInfo() {
        try {
            FingerprintManager fingerprintManager = (FingerprintManager) getSystemService(Context.FINGERPRINT_SERVICE);
            Method method = FingerprintManager.class.getDeclaredMethod("getEnrolledFingerprints");
            Object obj = method.invoke(fingerprintManager);
            if (obj != null) {
                Class<?> clazz = Class.forName("android.hardware.fingerprint.Fingerprint");
                Method getFingerId = clazz.getDeclaredMethod("getFingerId");
                for (int i = 0; i < ((List) obj).size(); i++) {
                    Object item = ((List) obj).get(i);
                    if (null == item) {
                        continue;
                    }
                    Log.i("wang", "fingerId: " + getFingerId.invoke(item));
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始指纹识别
     */
    private void startFingerprintRecognition() {

        mFingerprintCore = new FingerprintCore(this);
        mFingerprintCore.setFingerprintManager(mResultListener);

        if (!mFingerprintCore.isHardwareSupport()) { //判断是否有指纹识别硬件支持
            showToast(R.string.fingerprint_recognition_not_support);
            return;
        }
        if (!mFingerprintCore.isKeyguardSecure(this)) { //判断是否有设置密码锁屏
            showToast(R.string.fingerprint_recognition_not_supports);
            return;
        }
        if (!mFingerprintCore.isHasEnrolledFingerprints()) { //判断是否有录入指纹
            showToast(R.string.fingerprint_recognition_not_enrolled);
            return;
        }
        mFingerprintCore.startAuthenticate();
    }

    private FingerprintCore.IFingerprintResultListener mResultListener = new FingerprintCore.IFingerprintResultListener() {

        @Override
        public void onAuthenticateStart() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showFingerprintDialog(MainActivity.this);
                }
            });
        }

        @Override
        public void onAuthenticateSuccess() {
            showToast(R.string.fingerprint_recognition_success);
            if (!MainActivity.this.isFinishing() && dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }

        @Override
        public void onAuthenticateFailed() {
            showToast(R.string.fingerprint_recognition_failed);
        }

        @Override
        public void onAuthenticateHelp(CharSequence helpString) {
            if (helpString != null) showToast(helpString.toString());
        }

        @Override
        public void onAuthenticateError(CharSequence errString) {
            if (errString != null) showToast(errString.toString());
            if (!MainActivity.this.isFinishing() && dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    };

    private void showToast(int messageId) {
        Toast.makeText(MainActivity.this, messageId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(String messageId) {
        Toast.makeText(MainActivity.this, messageId, Toast.LENGTH_SHORT).show();
    }

    /**
     * 指纹识别弹框
     *
     * @param activity
     * @return
     */
    private void showFingerprintDialog(Activity activity) {
        dialog = new AlertDialog.Builder(activity).create();
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_fingerprint, null);
        TextView btnCancel = (TextView) view.findViewById(R.id.btn_cancel);
        LinearLayout linearLayout = (LinearLayout) view.findViewById(R.id.ll_parent);
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            Display display = windowManager.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            linearLayout.setLayoutParams(new FrameLayout.LayoutParams((int) (width * 0.8), LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                mFingerprintCore.cancelAuthenticate();
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                mFingerprintCore.cancelAuthenticate();
            }
        });

        dialog.setView(view);
        if (!activity.isFinishing()) {
            dialog.show();
        }
    }

    @Override
    protected void onDestroy() {
        if (mFingerprintCore != null) {
            mFingerprintCore.onDestroy();
            mFingerprintCore = null;
        }
        mResultListener = null;
        super.onDestroy();
    }

}
