package com.example.android.cardemulation;

import java.util.Arrays;
import java.util.Map;

final class RadioConfiguration {
    static final int[] PARAMETERS = {0x30, 0x31, 0x32, 0x33, 0x58, 0x59};
    static void requireBaseline(byte[] controller, Map<Integer, byte[]> nci) throws VendorFailure {
        if (controller == null || controller.length < 82 || controller[20] != 0 || controller[26] != 32)
            throw new VendorFailure("CHECK_BASELINE", "CONTROLLER_BASELINE_MISMATCH", null);
        for (int id : PARAMETERS) {
            byte[] expected = id == 0x30 ? new byte[]{4} : id == 0x31 ? new byte[]{0}
                    : id == 0x32 ? new byte[]{32} : new byte[0];
            byte[] actual = nci.get(id);
            if (!Arrays.equals(actual, expected))
                throw new VendorFailure("CHECK_BASELINE", "NCI_" + Integer.toHexString(id).toUpperCase(java.util.Locale.ROOT)
                        + "_BASELINE_MISMATCH", null);
        }
    }
    static void requireEnabled(byte[] controller, Map<Integer, byte[]> nci, AccessCardProfile profile) throws VendorFailure {
        if (controller == null || controller.length < 82 || controller[20] != 4 || controller[26] != profile.sak
                || !Arrays.equals(Arrays.copyOfRange(controller, 72, 76), profile.uid)
                || !Arrays.equals(nci.get(0x33), profile.uid)
                || !Arrays.equals(nci.get(0x30), new byte[]{profile.atqa[0]})
                || !Arrays.equals(nci.get(0x31), new byte[]{profile.atqa[1]})
                || !Arrays.equals(nci.get(0x32), new byte[]{profile.sak}))
            throw new VendorFailure("VERIFY_ENABLE", "ENABLE_READBACK_MISMATCH", null);
    }
}
