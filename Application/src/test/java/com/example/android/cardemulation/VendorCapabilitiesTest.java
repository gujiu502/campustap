package com.example.android.cardemulation;

import org.junit.Test;
import static org.junit.Assert.*;

public class VendorCapabilitiesTest {
    public interface Oplus {
        boolean setHceTypeAConfig(boolean enabled, byte[] atqa, byte[] sak, byte[] uid);
        String getHceTypeAUid();
        boolean hasFeature(String feature);
    }
    public static class Factory {
        static { if (true) throw new AssertionError("Discovery must not initialize vendor code"); }
        public static Oplus getVendorNfcAdapter() { throw new AssertionError("Discovery must not connect"); }
    }
    public static class WrongFactory { public Oplus getVendorNfcAdapter() { return null; } }
    public static class UntypedFactory { public static Object getVendorNfcAdapter() { return null; } }
    public static class SetterOnly {
        public boolean setHceTypeAConfig(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) { throw new AssertionError(); }
    }
    public static class WrongGetter extends SetterOnly { public byte[] getHceTypeAUid() { return null; } }
    public static class NoFeature extends SetterOnly { public String getHceTypeAUid() { return ""; } }
    public static class Realme13 extends SetterOnly {
        public boolean hasFeature(String name) { throw new AssertionError("Signature inspection must not call the service"); }
    }
    public static class StaticMethods {
        public static boolean setHceTypeAConfig(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) { return false; }
        public static String getHceTypeAUid() { return ""; }
    }
    public static class Routes {
        public static Routes getDefaultAdapter(Object context) { throw new AssertionError(); }
        public java.util.Map<?, ?> getUserDefaultRoutes() { throw new AssertionError(); }
    }
    @Test public void detectsTypedOplusWithoutInitializingConnectingOrWriting() {
        assertTrue(VendorCapabilities.oplusIdentityApi(Oplus.class));
        assertSame(Oplus.class, VendorCapabilities.oplusFactoryApi(Factory.class));
    }
    @Test public void rejectsIncompleteOrIncompatibleOplusApi() {
        assertFalse(VendorCapabilities.oplusIdentityApi(null));
        assertFalse(VendorCapabilities.oplusIdentityApi(SetterOnly.class));
        assertFalse(VendorCapabilities.oplusIdentityApi(WrongGetter.class));
        assertFalse(VendorCapabilities.oplusIdentityApi(NoFeature.class));
        assertFalse(VendorCapabilities.oplusIdentityApi(StaticMethods.class));
        assertNull(VendorCapabilities.oplusFactoryApi(WrongFactory.class));
        assertNull(VendorCapabilities.oplusFactoryApi(UntypedFactory.class));
    }
    @Test public void requiresReadableStRoutesBeforeSelectingWritableBackend() {
        assertTrue(VendorCapabilities.stRoutes(Routes.class, Object.class));
        assertFalse(VendorCapabilities.stRoutes(Routes.class, String.class));
        assertFalse(VendorCapabilities.stRoutes(Object.class, Object.class));
        assertFalse(VendorCapabilities.stRoutes(null, Object.class));
    }
    @Test public void allowsOplusPreflightWhileRuntimeStillRequiresBaseline() {
        assertEquals("", VendorCapabilities.unavailableCode(true, true));
        assertEquals("", VendorCapabilities.unavailableCode(false, true));
        assertEquals("VENDOR_INTERFACE_UNAVAILABLE", VendorCapabilities.unavailableCode(false, false));
    }
    @Test public void directOplusDoesNotNeedStGrantAndExistingAuthorizedStKeepsPriority() {
        assertTrue(VendorCapabilities.useOplus(false, true, false));
        assertTrue(VendorCapabilities.useOplus(false, true, true));
        assertTrue(VendorCapabilities.useOplus(true, true, false));
        assertFalse(VendorCapabilities.useOplus(true, true, true));
        assertFalse(VendorCapabilities.useOplus(false, false, false));
        assertFalse(VendorCapabilities.useOplus(false, false, true));
        assertFalse(VendorCapabilities.useOplus(true, false, false));
        assertFalse(VendorCapabilities.useOplus(true, false, true));
    }
    @Test public void reportsWhichOplusContractIsMissingWithoutCallingVendorCode() {
        java.util.Map<String, String> setterOnly = VendorCapabilities.oplusMethodStatus(SetterOnly.class);
        assertEquals("MATCH", setterOnly.get("setHceTypeAConfig"));
        assertEquals("MISSING_OR_FILTERED", setterOnly.get("getHceTypeAUid"));
        assertEquals("MISSING_OR_FILTERED", setterOnly.get("hasFeature"));
        assertEquals("RETURN_TYPE_MISMATCH", VendorCapabilities.oplusMethodStatus(WrongGetter.class).get("getHceTypeAUid"));
        assertEquals("STATIC_METHOD", VendorCapabilities.oplusMethodStatus(StaticMethods.class).get("setHceTypeAConfig"));
        assertEquals("CLASS_UNAVAILABLE", VendorCapabilities.oplusMethodStatus(null).get("hasFeature"));
    }
    @Test public void factoryReturnTypeCanBeInspectedEvenWhenIdentityMethodsAreIncomplete() {
        assertSame(Object.class, VendorCapabilities.oplusFactoryType(UntypedFactory.class));
        assertNull(VendorCapabilities.oplusFactoryApi(UntypedFactory.class));
        assertSame(Oplus.class, VendorCapabilities.oplusFactoryType(Factory.class));
        assertNull(VendorCapabilities.oplusFactoryType(WrongFactory.class));
    }
    @Test public void candidateReportSeparatesClassPresenceFromActualStGrant() {
        assertEquals("ST_PERMISSION_REQUIRED", VendorCapabilities.backendCandidate(true, false, false));
        assertEquals("ST", VendorCapabilities.backendCandidate(true, false, true));
        assertEquals("OPLUS_DIRECT_EXPERIMENTAL", VendorCapabilities.backendCandidate(true, true, false));
        assertEquals("ST", VendorCapabilities.backendCandidate(true, true, true));
        assertEquals("NONE", VendorCapabilities.backendCandidate(false, false, true));
    }
    @Test public void chipReportDoesNotExportArbitraryStringsOrIdentityData() {
        assertEquals("SN100T", VendorCapabilities.chipIdentifier("sn100t"));
        assertEquals("ST54J", VendorCapabilities.chipIdentifier("ST54J"));
        assertEquals("UNKNOWN", VendorCapabilities.chipIdentifier(null));
        assertEquals("UNKNOWN", VendorCapabilities.chipIdentifier("11223344"));
        assertEquals("UNKNOWN", VendorCapabilities.chipIdentifier("SN100T extra private content"));
    }
    @Test public void distinguishesTheObservedRealme13ContractFromTheOnePlusContract() {
        java.util.Map<String, String> realme = VendorCapabilities.oplusMethodStatus(Realme13.class);
        assertEquals("MATCH", realme.get("setHceTypeAConfig"));
        assertEquals("MATCH", realme.get("hasFeature"));
        assertEquals("MISSING_OR_FILTERED", realme.get("getHceTypeAUid"));
        assertFalse(VendorCapabilities.oplusIdentityApi(Realme13.class));
        assertTrue(VendorCapabilities.oplusIdentityApi(Oplus.class));
    }
}
