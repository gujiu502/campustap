package com.example.android.cardemulation;

import org.junit.Test;
import static org.junit.Assert.*;

public class OplusSystemStateTest {
    @Test public void missingDeniedOrUnknownValuesAreNeverInterpretedAsFalse() {
        assertEquals("EMPTY_OR_INACCESSIBLE", OplusSystemState.booleanStatus(null));
        assertEquals("EMPTY_OR_INACCESSIBLE", OplusSystemState.booleanStatus(" \n"));
        assertEquals("UNRECOGNIZED", OplusSystemState.booleanStatus("permission denied"));
        assertEquals("UNRECOGNIZED", OplusSystemState.booleanStatus("2"));
    }
    @Test public void reportsOnlyRecognizedBooleanValuesWithoutExportingPropertyText() {
        for (String value : new String[]{"1", "true", "yes", "Y", "on"})
            assertEquals("TRUE", OplusSystemState.booleanStatus(value));
        for (String value : new String[]{"0", "false", "no", "N", " off\n"})
            assertEquals("FALSE", OplusSystemState.booleanStatus(value));
        assertEquals("UNRECOGNIZED", OplusSystemState.booleanStatus("private unexpected text"));
    }
}
