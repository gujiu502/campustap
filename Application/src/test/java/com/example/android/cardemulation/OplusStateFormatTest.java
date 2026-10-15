package com.example.android.cardemulation;

import org.junit.Test;
import static org.junit.Assert.*;

public class OplusStateFormatTest {
    @Test public void normalizesEquivalentHexWithoutChangingBytes() throws Exception {
        for (String raw : new String[]{"0102A0FF", "01 02 A0 FF", "01:02:A0:FF", "01-02-A0-FF", "0x0102A0FF", " 0X01:02:A0:FF "})
            assertEquals("0102a0ff", OplusStateFormat.parse(OplusStateFormat.Field.UID, raw));
        assertEquals("0", OplusStateFormat.parse(OplusStateFormat.Field.MODE, " 0 "));
    }
    @Test public void equivalentFormattingPassesTransactionStateComparison() throws Exception {
        OplusTransaction.State compact = new OplusTransaction.State("0", "0400", "20", "01020304", "01020304");
        OplusTransaction.State separated = new OplusTransaction.State(" 0 ", "04:00", "0x20", "01 02 03 04", "0x01020304");
        assertTrue(compact.same(separated)); separated.requireBaseline();
    }
    @Test public void distinguishesMissingValuesFromUnsupportedEncodingWithoutInventingDefaults() {
        VendorFailure missing = assertThrows(VendorFailure.class, () -> OplusStateFormat.parse(OplusStateFormat.Field.UID, null));
        assertEquals("OPLUS_READ_UID", missing.stage); assertEquals("OPLUS_STATE_MISSING", missing.code);
        assertEquals("OPLUS_STATE_MISSING", OplusStateFormat.status(OplusStateFormat.Field.MODE, " "));
        assertEquals("OPLUS_STATE_FORMAT_UNSUPPORTED", OplusStateFormat.status(OplusStateFormat.Field.MODE, "false"));
        assertEquals("OPLUS_STATE_FORMAT_UNSUPPORTED", OplusStateFormat.status(OplusStateFormat.Field.UID, "a".repeat(129)));
    }
    @Test public void doesNotGuessDecimalArrayOddHexOrByteOrder() throws Exception {
        assertEquals("OPLUS_STATE_FORMAT_UNSUPPORTED", OplusStateFormat.status(OplusStateFormat.Field.ATQA, "[4, 0]"));
        assertEquals("OPLUS_STATE_FORMAT_UNSUPPORTED", OplusStateFormat.status(OplusStateFormat.Field.UID, "01020304F"));
        assertEquals("0004", OplusStateFormat.parse(OplusStateFormat.Field.ATQA, "00:04"));
        OplusTransaction.State reversed = new OplusTransaction.State("0", "00:04", "20", "01020304", "01020304");
        assertThrows(VendorFailure.class, reversed::requireBaseline);
    }
    @Test public void statusesDoNotReturnIdentityOrUnknownVendorStrings() {
        assertEquals("VALID", OplusStateFormat.status(OplusStateFormat.Field.REPORTED_UID, "11223344"));
        assertEquals("OPLUS_STATE_FORMAT_UNSUPPORTED", OplusStateFormat.status(OplusStateFormat.Field.REPORTED_UID, "private vendor value"));
    }
    @Test public void mismatchedUidSourcesRemainAnErrorAfterNormalization() {
        VendorFailure error = assertThrows(VendorFailure.class, () -> new OplusTransaction.State("0", "0000", "00", "01:02:03:04", "11:22:33:44"));
        assertEquals("OPLUS_UID_STATE_MISMATCH", error.code); assertFalse(error.description().contains("11223344"));
    }
}
