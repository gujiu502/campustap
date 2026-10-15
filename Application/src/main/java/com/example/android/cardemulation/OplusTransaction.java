package com.example.android.cardemulation;

import java.util.Arrays;
import java.util.Locale;

/** Experimental vendor-state transaction, not a controller or RF verification. */
final class OplusTransaction {
    interface Port {
        State read() throws Exception;
        boolean set(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) throws Exception;
    }
    interface Recovery {
        Snapshot load() throws Exception;
        void save(Snapshot snapshot) throws Exception;
        void clear() throws Exception;
    }
    static final class State {
        final String mode, atqa, sak, uid, reportedUid;
        State(String mode, String atqa, String sak, String uid, String reportedUid) throws VendorFailure {
            if (!("0".equals(mode) || "1".equals(mode))) throw failure("OPLUS_STATE_UNREADABLE");
            this.mode = mode;
            this.atqa = checkedHex(atqa, 4);
            this.sak = checkedHex(sak, 2);
            this.uid = checkedHex(uid, 8, 14, 20);
            this.reportedUid = checkedHex(reportedUid, 8, 14, 20);
            if (!this.uid.equals(this.reportedUid)) throw failure("OPLUS_STATE_UNREADABLE");
        }
        void requireBaseline() throws VendorFailure {
            // shortcut: only inactive, explicitly readable default/sentinel states; expand after ROM calibration.
            if (!"0".equals(mode) || !("0000".equals(atqa) || "0400".equals(atqa))
                    || !("00".equals(sak) || "20".equals(sak))) throw failure("OPLUS_BASELINE_MISMATCH");
        }
        boolean same(State other) {
            return mode.equals(other.mode) && atqa.equals(other.atqa) && sak.equals(other.sak)
                    && uid.equals(other.uid) && reportedUid.equals(other.reportedUid);
        }
        boolean identity(AccessCardProfile profile) {
            return Arrays.equals(bytes(atqa), profile.atqa) && Arrays.equals(bytes(sak), new byte[]{profile.sak})
                    && Arrays.equals(bytes(uid), profile.uid);
        }
    }
    static final class Snapshot {
        final State before;
        final AccessCardProfile requested;
        final boolean attempted;
        Snapshot(State before, AccessCardProfile requested) throws VendorFailure {
            this(before, requested, false);
        }
        Snapshot(State before, AccessCardProfile requested, boolean attempted) throws VendorFailure {
            before.requireBaseline();
            this.before = before;
            this.requested = new AccessCardProfile(requested.uid, requested.atqa, requested.sak & 255);
            this.attempted = attempted;
        }
    }
    private final Port port;
    private final Recovery recovery;
    OplusTransaction(Port port, Recovery recovery) { this.port = port; this.recovery = recovery; }

    void enable(AccessCardProfile profile) throws Exception {
        if (recovery.load() != null) restore();
        Snapshot snapshot = new Snapshot(port.read(), profile);
        recovery.save(snapshot); // The store must encrypt, flush and verify before returning.
        try {
            if (!snapshot.before.same(port.read())) throw failure("OPLUS_STATE_CHANGED");
            recovery.save(new Snapshot(snapshot.before, profile, true));
            if (!port.set(true, profile.atqa.clone(), new byte[]{profile.sak}, profile.uid.clone()))
                throw failure("OPLUS_SET_REJECTED");
            verify(profile);
        } catch (Exception | LinkageError error) {
            try { restore(); } catch (Exception | LinkageError rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }
    void verify(AccessCardProfile profile) throws Exception {
        State current = port.read();
        if (!"1".equals(current.mode) || !current.identity(profile)) throw failure("OPLUS_ENABLE_MISMATCH");
    }
    void restore() throws Exception {
        Snapshot saved = recovery.load();
        if (saved == null) return;
        saved.before.requireBaseline();
        if (!saved.attempted) { recovery.clear(); return; }
        State current = port.read();
        // A setter may change RF before updating its metadata: even unchanged metadata needs disarming.
        if (!saved.before.same(current) && !current.identity(saved.requested)) throw failure("OPLUS_STATE_CHANGED");
        if (!port.set(false, bytes(saved.before.atqa), bytes(saved.before.sak), bytes(saved.before.uid)))
            throw failure("OPLUS_RESTORE_REJECTED");
        if (!saved.before.same(port.read())) throw failure("RESTORE_READBACK_MISMATCH");
        recovery.clear();
    }
    static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte element : value) result.append(Character.forDigit((element & 255) >> 4, 16)).append(Character.forDigit(element & 15, 16));
        return result.toString();
    }
    static byte[] bytes(String value) {
        if (value == null || value.length() % 2 != 0 || value.length() > 20 || !value.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("Invalid encoded byte array");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
    private static String checkedHex(String value, int... lengths) throws VendorFailure {
        if (value != null && value.matches("[0-9a-fA-F]+"))
            for (int length : lengths) if (value.length() == length) return value.toLowerCase(Locale.ROOT);
        throw failure("OPLUS_STATE_UNREADABLE");
    }
    private static VendorFailure failure(String code) { return new VendorFailure("OPLUS_TRANSACTION", code, null); }
}
