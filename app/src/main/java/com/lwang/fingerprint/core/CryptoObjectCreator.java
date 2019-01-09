package com.lwang.fingerprint.core;

import android.annotation.TargetApi;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

@TargetApi(Build.VERSION_CODES.M)
public class CryptoObjectCreator {

    private static final String KEY_NAME = "crypto_object_fingerprint_key";
    private static final String KEYSTORE_NAME = "AndroidKeyStore";

    private FingerprintManager.CryptoObject mCryptoObject;
    private KeyStore mKeyStore;
    private KeyGenerator mKeyGenerator;
    private Cipher mCipher;


    public CryptoObjectCreator(ICryptoObjectCreateListener createListener) {
        mKeyStore = providesKeystore();
        mKeyGenerator = providesKeyGenerator();
        mCipher = providesCipher();
        if (mKeyStore != null && mKeyGenerator != null && mCipher != null) {
            mCryptoObject = new FingerprintManager.CryptoObject(mCipher);
        }
        prepareData(createListener);
    }

    /**
     * 创建KeyStore密钥库，用来存放密钥
     *
     * @return
     */
    public static KeyStore providesKeystore() {
        try {
            return KeyStore.getInstance(KEYSTORE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 创建KeyGenerator对象，获取密钥生成工具，用来生成密钥
     *
     * @return
     */
    public static KeyGenerator providesKeyGenerator() {
        try {
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 创建Cipher对象
     *
     * @return
     */
    public static Cipher providesCipher() {
        try {
            return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (Throwable e) {
            return null;
        }
    }

    private void prepareData(final ICryptoObjectCreateListener createListener) {
        new Thread() {
            @Override
            public void run() {
                try {
                    if (mCryptoObject != null) {
                        createKey();
                        initCipher();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (createListener != null) {
                    createListener.onDataPrepared(mCryptoObject);
                }
            }
        }.start();
    }

    /**
     * 用KeyGenerator密钥工具来生成密钥key
     */
    private void createKey() {
        try {
            mKeyStore.load(null);
            mKeyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            mKeyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | CertificateException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 初始化Cipher对象，在验证指纹时被用到
     */
    private void initCipher() {
        try {
            mKeyStore.load(null);
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
            mCipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (KeyPermanentlyInvalidatedException e) {
            e.printStackTrace();
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException("初始化 cipher 失败", e);
        }
    }

    public FingerprintManager.CryptoObject getCryptoObject() {
        return mCryptoObject;
    }

    public interface ICryptoObjectCreateListener {
        void onDataPrepared(FingerprintManager.CryptoObject cryptoObject);
    }

    public void onDestroy() {
        mCipher = null;
        mCryptoObject = null;
        mCipher = null;
        mKeyStore = null;
    }

}
