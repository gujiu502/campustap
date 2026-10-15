package com.example.android.cardemulation;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class OplusConnectionTest {
    public static class Api {
        boolean feature = true, deny, failUid;
        String uid = "";
        int writes, features;
        public boolean hasFeature(String name) {
            features++;
            if (deny) throw new SecurityException("Unavailable client");
            return feature;
        }
        public String getHceTypeAUid() {
            if (failUid) throw new UnsupportedOperationException("Client stub without service");
            return uid;
        }
        public boolean setHceTypeAConfig(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) { writes++; return true; }
    }
    public static class Factory {
        static Api adapter;
        public static Api getVendorNfcAdapter() { return adapter; }
    }
    private static OplusConnection.Endpoint platform() {
        return new OplusConnection.Endpoint("PLATFORM", Api.class, null);
    }
    private static OplusConnection.Endpoint vendor() throws Exception {
        return new OplusConnection.Endpoint("ROM:/system_ext/framework/nfcvendorlib.jar", Api.class,
                Factory.class.getMethod("getVendorNfcAdapter"));
    }
    private static String emptyInactive(String suffix) { return "mode".equals(suffix) ? "0" : null; }

    @Test public void deniedPlatformFallsBackToVendorUsingOnlyReads() throws Exception {
        Api platform = new Api(); platform.deny = true;
        Factory.adapter = new Api();
        OplusConnection connection = OplusConnection.select(Arrays.asList(platform(), vendor()), platform,
                OplusConnectionTest::emptyInactive, "", true);
        assertTrue(connection.endpoint.id.startsWith("ROM:"));
        assertTrue(connection.read().uninitializedInactive());
        assertEquals(0, platform.writes); assertEquals(0, Factory.adapter.writes);
    }
    @Test public void unavailableUidGetterDoesNotHideTheNextCompleteClient() throws Exception {
        Api platform = new Api(); platform.failUid = true;
        Factory.adapter = new Api();
        OplusConnection connection = OplusConnection.select(Arrays.asList(platform(), vendor()), platform,
                OplusConnectionTest::emptyInactive, "", true);
        assertEquals(vendor().id, connection.endpoint.id);
        assertEquals(0, platform.writes); assertEquals(0, Factory.adapter.writes);
    }
    @Test public void disabledFeatureCanSelectAnotherClientButNeverEnableDuringDiscovery() throws Exception {
        Api platform = new Api(); platform.feature = false;
        Factory.adapter = new Api();
        assertEquals(vendor().id, OplusConnection.select(Arrays.asList(platform(), vendor()), platform,
                OplusConnectionTest::emptyInactive, "", true).endpoint.id);
        assertEquals(0, platform.writes); assertEquals(0, Factory.adapter.writes);
    }
    @Test public void activePartialAndUnknownModesRemainBlockedAcrossAllClients() throws Exception {
        for (String mode : Arrays.asList("1", "", "?")) {
            Api platform = new Api(); Factory.adapter = new Api();
            assertThrows(VendorFailure.class, () -> OplusConnection.select(Arrays.asList(platform(), vendor()), platform,
                    suffix -> "mode".equals(suffix) ? mode : null, "", true));
            assertEquals(0, platform.writes); assertEquals(0, Factory.adapter.writes);
        }
        Api platform = new Api();
        assertThrows(VendorFailure.class, () -> OplusConnection.select(Collections.singletonList(platform()), platform,
                suffix -> "mode".equals(suffix) ? "0" : "sak".equals(suffix) ? "00" : null, "", true));
        assertEquals(0, platform.writes);
    }
    @Test public void recoveryCannotSwitchToAnotherInterfaceEvenWhenItWouldWork() throws Exception {
        Factory.adapter = new Api();
        VendorFailure missing = assertThrows(VendorFailure.class, () -> OplusConnection.select(
                Collections.singletonList(vendor()), new Api(), OplusConnectionTest::emptyInactive, "PLATFORM", false));
        assertEquals("OPLUS_RECOVERY_INTERFACE_UNAVAILABLE", missing.code);
        assertEquals(0, Factory.adapter.features); assertEquals(0, Factory.adapter.writes);
        Factory.adapter = null;
        Api platform = new Api();
        assertThrows(VendorFailure.class, () -> OplusConnection.select(Arrays.asList(platform(), vendor()), platform,
                OplusConnectionTest::emptyInactive, vendor().id, false));
        assertEquals(0, platform.features); assertEquals(0, platform.writes);
    }
    @Test public void recoveryDoesNotRequireFeatureStillEnabledOrAnInactiveState() throws Exception {
        Api platform = new Api(); platform.deny = true;
        OplusConnection connection = OplusConnection.select(Collections.singletonList(platform()), platform,
                suffix -> { throw new AssertionError("Recovery selection must leave state validation to the journal transaction"); }, "PLATFORM", false);
        assertTrue(connection.set(false, new byte[2], new byte[1], new byte[4]));
        assertEquals(0, platform.features); assertEquals(1, platform.writes);
    }
    @Test public void journalPinsNewEndpointAndKeepsOldReleaseDefault() throws Exception {
        assertEquals("PLATFORM", OplusConnection.recoveryEndpoint(1, "ignored", "PLATFORM"));
        assertEquals(vendor().id, OplusConnection.recoveryEndpoint(2, vendor().id, "PLATFORM"));
        assertThrows(VendorFailure.class, () -> OplusConnection.recoveryEndpoint(2, "", "PLATFORM"));
        assertThrows(VendorFailure.class, () -> OplusConnection.recoveryEndpoint(3, "PLATFORM", "PLATFORM"));
        assertThrows(VendorFailure.class, () -> OplusConnection.recoveryEndpoint(1, "", ""));
    }
    @Test public void aRejectedWriteNeverTriggersTryingAnotherClient() throws Exception {
        Api platform = new Api() {
            @Override public boolean setHceTypeAConfig(boolean enabled, byte[] a, byte[] s, byte[] u) { writes++; return false; }
        };
        Factory.adapter = new Api();
        OplusConnection connection = OplusConnection.select(Arrays.asList(platform(), vendor()), platform,
                OplusConnectionTest::emptyInactive, "", true);
        assertFalse(connection.set(true, new byte[2], new byte[1], new byte[4]));
        assertEquals(1, platform.writes); assertEquals(0, Factory.adapter.features); assertEquals(0, Factory.adapter.writes);
    }
}
