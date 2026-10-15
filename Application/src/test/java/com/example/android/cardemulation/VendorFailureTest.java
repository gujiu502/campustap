package com.example.android.cardemulation;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import static org.junit.Assert.*;

public class VendorFailureTest {
    @Test public void distinguishesPermissionFromUnsupportedDevicesAndNfcOff() {
        VendorFailure permission = new VendorFailure("CHECK_PERMISSION", "PERMISSION_REQUIRED", null);
        VendorFailure device = new VendorFailure("CHECK_DEVICE", "UNVERIFIED_DEVICE", null);
        VendorFailure off = new VendorFailure("CHECK_NFC", "NFC_OFF", null);
        assertTrue(permission.needsAuthorization());
        assertTrue(permission.description().contains("電腦授權"));
        assertFalse(device.needsAuthorization());
        assertTrue(device.description().contains("裝置適配"));
        assertFalse(off.needsAuthorization());
        assertTrue(off.description().contains("開啟 NFC"));
        assertFalse(new VendorFailure("CHECK_NFC", "NFC_UNAVAILABLE", null).needsAuthorization());
        assertTrue(VendorFailure.at("PROGRAM", new InvocationTargetException(new SecurityException())).needsAuthorization());
    }
    @Test public void unwrapsReflectionAndNeverExportsTheExceptionMessage() {
        VendorFailure failure = VendorFailure.at("PROGRAM_TEST_CONFIGURATION", new InvocationTargetException(
                new InvocationTargetException(new SecurityException("sensitive card value"))));
        assertEquals("SecurityException", failure.errorType());
        assertEquals("CALL_FAILED", failure.getMessage());
        assertFalse(failure.description().contains("sensitive"));
        assertTrue(failure.description().contains("權限"));
    }
    @Test public void preservesTheSpecificReadFailure() {
        VendorFailure failure = new VendorFailure("READ_NCI_33", "NO_NCI_READBACK", null);
        assertSame(failure, VendorFailure.at("SNAPSHOT", failure));
        assertEquals("ValidationFailure", failure.errorType());
        assertTrue(failure.description().contains("不能安全設定"));
    }
}
