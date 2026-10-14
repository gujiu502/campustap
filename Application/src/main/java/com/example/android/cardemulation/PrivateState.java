package com.example.android.cardemulation;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class PrivateState {
    private final Context context;
    PrivateState(Context context) { this.context = context.getApplicationContext(); }
    private AtomicFile file(String name) { return new AtomicFile(new File(context.getFilesDir(), name)); }
    private synchronized SecretKey key() throws Exception {
        String alias = "campustap.private-state";
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(alias, null);
    }
    boolean exists(String name) {
        File base = file(name).getBaseFile();
        return base.exists() || new File(base.getPath() + ".bak").exists();
    }
    void write(String name, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(plaintext), iv = cipher.getIV();
        if (iv.length != 12) throw new IllegalStateException("Unexpected GCM IV length");
        AtomicFile target = file(name);
        FileOutputStream out = target.startWrite();
        try { out.write(iv); out.write(encrypted); target.finishWrite(out); }
        catch (Exception e) { target.failWrite(out); throw e; }
    }
    byte[] read(String name) throws Exception {
        byte[] bytes = file(name).readFully();
        if (bytes.length < 28 || bytes.length > 8192) throw new IllegalStateException("Invalid private state size");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
        return cipher.doFinal(bytes, 12, bytes.length - 12);
    }
    void delete(String name) throws Exception {
        file(name).delete();
        if (exists(name)) throw new IllegalStateException("Private state cleanup failed");
    }
}
