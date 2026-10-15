package com.example.android.cardemulation;

import java.util.Arrays;

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
            this.mode = OplusStateFormat.parse(OplusStateFormat.Field.MODE, mode);
            if ("0".equals(this.mode) && absent(atqa) && absent(sak) && absent(uid) && absent(reportedUid)) {
                this.atqa = this.sak = this.uid = this.reportedUid = "";
                return;
            }
            this.atqa = OplusStateFormat.parse(OplusStateFormat.Field.ATQA, atqa);
            this.sak = OplusStateFormat.parse(OplusStateFormat.Field.SAK, sak);
            this.uid = OplusStateFormat.parse(OplusStateFormat.Field.UID, uid);
            this.reportedUid = "0".equals(this.mode) && absent(reportedUid) ? ""
                    : OplusStateFormat.parse(OplusStateFormat.Field.REPORTED_UID, reportedUid);
            if (!this.reportedUid.isEmpty() && !this.uid.equals(this.reportedUid)) throw failure("OPLUS_UID_STATE_MISMATCH");
        }
        boolean uninitializedInactive() { return "0".equals(mode) && atqa.isEmpty() && sak.isEmpty() && uid.isEmpty() && reportedUid.isEmpty(); }
        void requireBaseline() throws VendorFailure {
            if (uninitializedInactive()) return;
            // shortcut: only inactive, explicitly readable default/sentinel states; expand after ROM calibration.
            if (!"0".equals(mode) || !("0000".equals(atqa) || "0400".equals(atqa))
                    || !("00".equals(sak) || "20".equals(sak))) throw failure("OPLUS_BASELINE_MISMATCH");
        }
        boolean same(State other) {
            return mode.equals(other.mode) && atqa.equals(other.atqa) && sak.equals(other.sak)
                    && uid.equals(other.uid) && reportedUid.equals(other.reportedUid);
        }
        boolean identity(AccessCardProfile profile) {
            return !uid.isEmpty() && Arrays.equals(bytes(atqa), profile.atqa) && Arrays.equals(bytes(sak), new byte[]{profile.sak})
                    && Arrays.equals(bytes(uid), profile.uid);
        }
        boolean restoredFrom(State original) {
            if (!original.uninitializedInactive()) return original.same(this);
            // No original identity existed: verify stopped override, not recreation of absent Settings keys.
            return uninitializedInactive() || ("0".equals(mode) && ("0000".equals(atqa) || "0400".equals(atqa))
                    && ("00".equals(sak) || "20".equals(sak)) && "00000011".equals(uid));
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

    boolean enable(AccessCardProfile profile) throws Exception {
        if (recovery.load() != null) restore();
        Snapshot snapshot = new Snapshot(port.read(), profile);
        recovery.save(snapshot); // The store must encrypt, flush and verify before returning.
        try {
            if (!snapshot.before.same(port.read())) throw failure("OPLUS_STATE_CHANGED");
            recovery.save(new Snapshot(snapshot.before, profile, true));
            if (!port.set(true, profile.atqa.clone(), new byte[]{profile.sak}, profile.uid.clone()))
                throw failure("OPLUS_SET_REJECTED");
            verify(profile);
            return snapshot.before.uninitializedInactive();
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
        if (!current.restoredFrom(saved.before) && !current.identity(saved.requested)) throw failure("OPLUS_STATE_CHANGED");
        // The published OEM disarm call takes these sentinel arguments; they are not a fabricated snapshot.
        boolean empty = saved.before.uninitializedInactive();
        if (!port.set(false, empty ? new byte[2] : bytes(saved.before.atqa), empty ? new byte[1] : bytes(saved.before.sak),
                empty ? new byte[]{0, 0, 0, 0x11} : bytes(saved.before.uid)))
            throw failure("OPLUS_RESTORE_REJECTED");
        if (!port.read().restoredFrom(saved.before)) throw failure("RESTORE_READBACK_MISMATCH");
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
    private static VendorFailure failure(String code) { return new VendorFailure("OPLUS_TRANSACTION", code, null); }
    private static boolean absent(String value) { return value == null || (value.length() <= 128 && value.trim().isEmpty()); }
}
