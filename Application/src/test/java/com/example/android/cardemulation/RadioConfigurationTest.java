package com.example.android.cardemulation;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class RadioConfigurationTest {
    private byte[] baseline() { byte[] bytes = new byte[91]; bytes[26] = 32; return bytes; }
    private Map<Integer, byte[]> parameters() {
        Map<Integer, byte[]> map = new HashMap<>();
        for (int id : RadioConfiguration.PARAMETERS) map.put(id, new byte[0]);
        map.put(0x30, new byte[]{4}); map.put(0x31, new byte[]{0}); map.put(0x32, new byte[]{32});
        return map;
    }
    @Test public void acceptsOnlyTheVerifiedBaseline() throws Exception {
        RadioConfiguration.requireBaseline(baseline(), parameters());
        for (int id : RadioConfiguration.PARAMETERS) {
            Map<Integer, byte[]> changed = parameters(); changed.put(id, new byte[]{99});
            VendorFailure failure = assertThrows(VendorFailure.class, () -> RadioConfiguration.requireBaseline(baseline(), changed));
            assertEquals("CHECK_BASELINE", failure.stage);
            assertEquals("NCI_" + Integer.toHexString(id).toUpperCase(java.util.Locale.ROOT) + "_BASELINE_MISMATCH", failure.code);
            changed.put(id, null);
            assertThrows(VendorFailure.class, () -> RadioConfiguration.requireBaseline(baseline(), changed));
        }
    }
    @Test public void rejectsUnknownOrTruncatedControllerWithoutReadingPastBounds() {
        for (byte[] bytes : new byte[][]{null, {}, new byte[26], new byte[81]})
            assertThrows(VendorFailure.class, () -> RadioConfiguration.requireBaseline(bytes, parameters()));
        byte[] fixedUid = baseline(); fixedUid[20] = 4;
        assertThrows(VendorFailure.class, () -> RadioConfiguration.requireBaseline(fixedUid, parameters()));
    }
    @Test public void requiresBothControllerAndNciToMatch() throws Exception {
        AccessCardProfile profile = new AccessCardProfile(new byte[]{1,2,3,4}, new byte[]{4,0}, 8);
        byte[] bytes = baseline(); bytes[20] = 4; bytes[26] = 8;
        System.arraycopy(profile.uid, 0, bytes, 72, 4);
        Map<Integer, byte[]> nci = parameters(); nci.put(0x32, new byte[]{8}); nci.put(0x33, profile.uid.clone());
        RadioConfiguration.requireEnabled(bytes, nci, profile);
        nci.put(0x33, new byte[]{9,2,3,4});
        assertThrows(VendorFailure.class, () -> RadioConfiguration.requireEnabled(bytes, nci, profile));
        nci.put(0x33, profile.uid); bytes[72] = 9;
        assertThrows(VendorFailure.class, () -> RadioConfiguration.requireEnabled(bytes, nci, profile));
        assertThrows(VendorFailure.class, () -> RadioConfiguration.requireEnabled(new byte[75], nci, profile));
    }
    @Test public void rejectsWrongAtqaEvenWhenUidAndSakMatch() {
        AccessCardProfile profile = new AccessCardProfile(new byte[]{1,2,3,4}, new byte[]{4,0}, 8);
        byte[] bytes = baseline(); bytes[20] = 4; bytes[26] = 8;
        System.arraycopy(profile.uid, 0, bytes, 72, 4);
        Map<Integer, byte[]> nci = parameters(); nci.put(0x32, new byte[]{8}); nci.put(0x33, profile.uid);
        nci.put(0x30, new byte[]{0});
        assertThrows(VendorFailure.class, () -> RadioConfiguration.requireEnabled(bytes, nci, profile));
    }
}
