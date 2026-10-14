package com.example.android.cardemulation;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import static org.junit.Assert.*;

public class VendorFailureTest {
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
