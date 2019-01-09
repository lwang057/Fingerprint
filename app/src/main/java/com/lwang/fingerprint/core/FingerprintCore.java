package com.lwang.fingerprint.core;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;

import java.lang.ref.WeakReference;

@TargetApi(Build.VERSION_CODES.M)
public class FingerprintCore {

    private FingerprintManager mFingerprintManager;
    private WeakReference<IFingerprintResultListener> listener;
    private CancellationSignal mCancellationSignal;
    private CryptoObjectCreator mCryptoObjectCreator;
    private FingerprintManager.AuthenticationCallback mAuthCallback;


    public FingerprintCore(Context context) {
        mFingerprintManager = getFingerprintManager(context);
        initCryptoObject();
    }

    public static FingerprintManager getFingerprintManager(Context context) {
        FingerprintManager fingerprintManager = null;
        try {
            fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return fingerprintManager;
    }

    private void initCryptoObject() {
        try {
            mCryptoObjectCreator = new CryptoObjectCreator(new CryptoObjectCreator.ICryptoObjectCreateListener() {
                @Override
                public void onDataPrepared(FingerprintManager.CryptoObject cryptoObject) {
                    // 如果需要一开始就进行指纹识别，可以在秘钥数据创建之后就启动指纹认证
//                    startAuthenticate(cryptoObject);
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 判断是否有指纹识别硬件支持
     *
     * @return
     */
    public boolean isHardwareSupport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        try {
            return mFingerprintManager != null && mFingerprintManager.isHardwareDetected();
        } catch (SecurityException e) {
        } catch (Throwable e) {
        }
        return false;
    }

    /**
     * 判断是否有设置密码锁屏，没有则不能使用指纹识别
     *
     * @return
     */
    public boolean isKeyguardSecure(Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        //判断设备是否处于安全保护中
        return keyguardManager.isKeyguardSecure();
    }

    /**
     * 判断是否有录入指纹，有些设备上即使录入了指纹，但是没有开启锁屏密码的话此方法还是返回false
     *
     * @return
     */
    public boolean isHasEnrolledFingerprints() {
        try {
            // 有些厂商api23之前的版本可能没有做好兼容，这个方法内部会崩溃（redmi note2, redmi note3等）
            return mFingerprintManager.hasEnrolledFingerprints();
        } catch (SecurityException e) {
        } catch (Throwable e) {
        }
        return false;
    }

    /**
     * 调用指纹识别
     */
    public void startAuthenticate() {
        startAuthenticate(mCryptoObjectCreator.getCryptoObject());
    }

    private void startAuthenticate(FingerprintManager.CryptoObject cryptoObject) {
        prepareData();
        try {
            mFingerprintManager.authenticate(cryptoObject, mCancellationSignal, 0, mAuthCallback, null);
        } catch (SecurityException e) {
            try {
                mFingerprintManager.authenticate(null, mCancellationSignal, 0, mAuthCallback, null);
            } catch (SecurityException e2) {
            } catch (Throwable throwable) {
            }
        } catch (Throwable throwable) {

        }
    }

    /**
     * 关闭指纹识别
     */
    public void cancelAuthenticate() {
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
        }
    }

    private void prepareData() {

        // 必须重新实例化，否则cancel 过一次就不能再使用了
        mCancellationSignal = new CancellationSignal();

        if (null != listener && null != listener.get()) {
            listener.get().onAuthenticateStart();
        }

        if (mAuthCallback == null) {
            mAuthCallback = new FingerprintManager.AuthenticationCallback() {

                @Override
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    Log.i("wang", "成功----->");
                    if (null != listener && null != listener.get()) {
                        listener.get().onAuthenticateSuccess();
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    Log.i("wang", "失败----->");
                    if (null != listener && null != listener.get()) {
                        listener.get().onAuthenticateFailed();
                    }
                }

                @Override
                public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                    // 建议根据参数helpString返回值，并且仅针对特定的机型做处理，并不能保证所有厂商返回的状态一致
                    Log.i("wang", "帮助----->helpMsgId:::" + helpMsgId + ", helpString:::" + helpString.toString());
                    if (null != listener && null != listener.get()) {
                        listener.get().onAuthenticateHelp(helpString);
                    }
                }

                @Override
                public void onAuthenticationError(int errMsgId, CharSequence errString) {
                    // 多次指纹密码验证错误后，进入此方法；并且，不能短时间内调用指纹验证,一般间隔从几秒到几十秒不等
                    // 这种情况不建议重试，建议提示用户用其他的方式解锁或者认证
                    Log.i("wang", "错误----->errMsgId:::" + errMsgId + ", errString:::" + errString);
                    if (null != listener && null != listener.get()) {
                        listener.get().onAuthenticateError(errString);
                    }
                }
            };
        }
    }

    public void onDestroy() {
        cancelAuthenticate();
        mAuthCallback = null;
        listener = null;
        mCancellationSignal = null;
        mFingerprintManager = null;
        if (mCryptoObjectCreator != null) {
            mCryptoObjectCreator.onDestroy();
            mCryptoObjectCreator = null;
        }
    }

    /**
     * 设置指纹识别接口回调
     *
     * @param listener
     */
    public void setFingerprintManager(IFingerprintResultListener listener) {
        this.listener = new WeakReference<>(listener);
    }

    /**
     * 指纹识别回调接口
     */
    public interface IFingerprintResultListener {

        /**
         * 指纹识别开始
         */
        void onAuthenticateStart();

        /**
         * 指纹识别成功
         */
        void onAuthenticateSuccess();

        /**
         * 指纹识别失败
         */
        void onAuthenticateFailed();

        /**
         * 指纹识别帮助
         */
        void onAuthenticateHelp(CharSequence helpString);

        /**
         * 指纹识别发生错误-不可短暂恢复
         */
        void onAuthenticateError(CharSequence errString);
    }

}
