package com.example.android.cardemulation;

import org.junit.Test;
import static org.junit.Assert.*;

public class OplusTransactionTest {
    private static final AccessCardProfile CARD = new AccessCardProfile(new byte[]{1, 2, 3, 4}, new byte[]{4, 0}, 8);
    private static OplusTransaction.State state(String mode, String atqa, String sak, String uid) throws Exception {
        return new OplusTransaction.State(mode, atqa, sak, uid, uid);
    }
    private static final class Store implements OplusTransaction.Recovery {
        OplusTransaction.Snapshot saved;
        boolean failSave;
        Runnable afterSave;
        @Override public OplusTransaction.Snapshot load() { return saved; }
        @Override public void save(OplusTransaction.Snapshot value) throws Exception {
            if (failSave) throw new java.io.IOException("Storage unavailable");
            saved = value;
            if (afterSave != null && !value.attempted) afterSave.run();
        }
        @Override public void clear() { saved = null; }
    }
    private static final class Port implements OplusTransaction.Port {
        final Store store;
        OplusTransaction.State current = state("0", "0000", "00", "00000011");
        boolean rejectEnable, rejectRestore, ignoreEnable, mismatchEnable, failReads;
        boolean partialEnable, failReadsAfterEnable, mismatchRestore, linkageAfterEnable;
        int enables, restores;
        Port(Store store) throws Exception { this.store = store; }
        @Override public OplusTransaction.State read() throws Exception {
            if (failReads) throw new java.io.IOException("Service unavailable");
            return current;
        }
        @Override public boolean set(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) throws Exception {
            assertNotNull("No hardware call without a verified recovery snapshot", store.saved);
            assertTrue("Write-attempt marker must be durable before calling the vendor", store.saved.attempted);
            if (enabled) {
                enables++;
                if (!ignoreEnable && (!rejectEnable || partialEnable))
                    current = state("1", OplusTransaction.hex(atqa), OplusTransaction.hex(sak), mismatchEnable ? "11121314" : OplusTransaction.hex(uid));
                if (failReadsAfterEnable) failReads = true;
                if (linkageAfterEnable) throw new UnsatisfiedLinkError("Vendor runtime unavailable");
                return !rejectEnable;
            }
            restores++;
            if (rejectRestore) return false;
            current = state("0", OplusTransaction.hex(atqa), OplusTransaction.hex(sak), OplusTransaction.hex(uid));
            if (mismatchRestore) current = state("0", "0400", "20", OplusTransaction.hex(uid));
            return true;
        }
    }
    @Test public void writesOnlyAfterSnapshotAndRestoresAfterProcessRestart() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        OplusTransaction.State before = port.current;
        new OplusTransaction(port, store).enable(CARD);
        assertEquals(1, port.enables); assertNotNull(store.saved);
        new OplusTransaction(port, store).verify(CARD);
        new OplusTransaction(port, store).restore();
        assertTrue(before.same(port.current)); assertNull(store.saved); assertEquals(1, port.restores);
    }
    @Test public void refusesAnExistingActiveCardWithoutWriting() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        port.current = state("1", "0400", "08", "11121314");
        VendorFailure error = assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals("OPLUS_BASELINE_MISMATCH", error.code); assertEquals(0, port.enables); assertNull(store.saved);
    }
    @Test public void refusesUnknownBaselineWithoutWriting() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        port.current = state("0", "4400", "20", "00000011");
        assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals(0, port.enables); assertNull(store.saved);
    }
    @Test public void rejectsMissingMalformedAndInconsistentStateWithoutLeakingValues() {
        VendorFailure missing = assertThrows(VendorFailure.class, () -> new OplusTransaction.State("0", null, "00", "00000011", "00000011"));
        assertEquals("OPLUS_STATE_UNREADABLE", missing.code);
        assertThrows(VendorFailure.class, () -> state("false", "0000", "00", "00000011"));
        assertThrows(VendorFailure.class, () -> state("0", "0000", "00", "not a card"));
        assertThrows(VendorFailure.class, () -> new OplusTransaction.State("0", "0000", "00", "00000011", "11121314"));
        assertFalse(missing.description().contains("00000011"));
    }
    @Test public void storageFailureMakesNoVendorWrite() throws Exception {
        Store store = new Store(); store.failSave = true; Port port = new Port(store);
        assertThrows(java.io.IOException.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals(0, port.enables); assertEquals(0, port.restores);
    }
    @Test public void rejectedEnableStillDisarmsBecauseRfMayHaveChangedBeforeMetadata() throws Exception {
        Store store = new Store(); Port port = new Port(store); port.rejectEnable = true;
        VendorFailure error = assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals("OPLUS_SET_REJECTED", error.code); assertNull(store.saved); assertEquals(1, port.restores);
    }
    @Test public void rejectedPartialEnableIsRolledBack() throws Exception {
        Store store = new Store(); Port port = new Port(store); port.rejectEnable = true; port.partialEnable = true;
        OplusTransaction.State before = port.current;
        assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertTrue(before.same(port.current)); assertEquals(1, port.restores); assertNull(store.saved);
    }
    @Test public void acceptedCallWithoutMatchingReadbackIsNotSuccess() throws Exception {
        Store store = new Store(); Port port = new Port(store); port.ignoreEnable = true;
        VendorFailure error = assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals("OPLUS_ENABLE_MISMATCH", error.code); assertNull(store.saved); assertEquals(1, port.restores);
    }
    @Test public void anotherIdentityIsNeverOverwrittenDuringRollback() throws Exception {
        Store store = new Store(); Port port = new Port(store); port.mismatchEnable = true;
        VendorFailure error = assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals("OPLUS_ENABLE_MISMATCH", error.code); assertEquals(1, error.getSuppressed().length);
        assertNotNull(store.saved); assertEquals(0, port.restores); assertEquals("11121314", port.current.uid);
    }
    @Test public void failedRecoveryRetainsSnapshotForRetry() throws Exception {
        Store store = new Store(); Port port = new Port(store); OplusTransaction transaction = new OplusTransaction(port, store);
        transaction.enable(CARD); port.rejectRestore = true;
        assertThrows(VendorFailure.class, transaction::restore); assertNotNull(store.saved);
        port.rejectRestore = false; transaction.restore(); assertNull(store.saved);
    }
    @Test public void serviceLossRetainsRecoveryAndDoesNotGuessDisableArguments() throws Exception {
        Store store = new Store(); Port port = new Port(store); port.failReadsAfterEnable = true;
        assertThrows(java.io.IOException.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertNotNull(store.saved); assertEquals(0, port.restores);
        port.failReads = false; new OplusTransaction(port, store).restore(); assertNull(store.saved);
    }
    @Test public void stateChangeBetweenSnapshotAndWriteAbortsWithoutOverwriting() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        OplusTransaction.State other = state("1", "0400", "08", "11121314");
        store.afterSave = () -> port.current = other;
        assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertEquals(0, port.enables); assertEquals(0, port.restores); assertNull(store.saved);
    }
    @Test public void clearsAlreadyRestoredJournalWithoutHardwareWrite() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        store.saved = new OplusTransaction.Snapshot(port.current, CARD);
        new OplusTransaction(port, store).restore(); assertNull(store.saved); assertEquals(0, port.restores);
    }
    @Test public void checksEveryEnabledParameterNotJustUid() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        port.current = state("1", "0400", "20", "01020304");
        assertThrows(VendorFailure.class, () -> new OplusTransaction(port, store).verify(CARD));
    }
    @Test public void successfulRestoreReturnStillNeedsMatchingReadback() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        OplusTransaction transaction = new OplusTransaction(port, store);
        transaction.enable(CARD); port.mismatchRestore = true;
        VendorFailure error = assertThrows(VendorFailure.class, transaction::restore);
        assertEquals("RESTORE_READBACK_MISMATCH", error.code); assertNotNull(store.saved);
    }
    @Test public void recoversCrashAfterAttemptMarkerBeforeSetterWasCalled() throws Exception {
        Store store = new Store(); Port port = new Port(store);
        store.saved = new OplusTransaction.Snapshot(port.current, CARD, true);
        new OplusTransaction(port, store).restore();
        assertEquals(0, port.enables); assertEquals(1, port.restores); assertNull(store.saved);
    }
    @Test public void vendorLinkageFailureAfterMutationStillAttemptsRecovery() throws Exception {
        Store store = new Store(); Port port = new Port(store); port.linkageAfterEnable = true;
        OplusTransaction.State before = port.current;
        assertThrows(UnsatisfiedLinkError.class, () -> new OplusTransaction(port, store).enable(CARD));
        assertTrue(before.same(port.current)); assertEquals(1, port.restores); assertNull(store.saved);
    }
    @Test public void hexRoundTripsAndRejectsOddOrNonHexEncoding() {
        assertArrayEquals(new byte[]{0, 8, (byte) 255}, OplusTransaction.bytes("0008FF"));
        assertEquals("0008ff", OplusTransaction.hex(new byte[]{0, 8, (byte) 255}));
        assertThrows(IllegalArgumentException.class, () -> OplusTransaction.bytes("01020304F"));
        assertThrows(IllegalArgumentException.class, () -> OplusTransaction.bytes("gg"));
    }
}
