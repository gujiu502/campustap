package com.example.android.cardemulation;

public final class AccessCardProfile {
    public final byte[] uid;
    public final byte[] atqa;
    public final byte sak;
    public AccessCardProfile(byte[] uid, byte[] atqa, int sak) {
        if (uid == null || uid.length != 4 || atqa == null || atqa.length != 2
                || atqa[0] != 4 || atqa[1] != 0 || sak != 8)
            throw new IllegalArgumentException("Unsupported card profile; this device test covers the observed Classic 1K type only");
        this.uid = uid.clone(); this.atqa = atqa.clone(); this.sak = (byte) sak;
    }
}
