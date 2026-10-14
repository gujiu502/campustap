package com.example.android.cardemulation;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

public class TestProtocolTest {
    @Test public void acceptsOnlyOwnAid() {
        assertArrayEquals("CAMPUS_TEST_ONLY\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1), TestProtocol.respond(TestProtocol.select()));
        assertArrayEquals(TestProtocol.respond(TestProtocol.select()), TestProtocol.respond(TestProtocol.hex("00A4040007F043414D50555300")));
        for (byte[] invalid : new byte[][]{null, {}, TestProtocol.hex("00A4040005F222222222"), TestProtocol.hex("00A4040007F043414D505552"), TestProtocol.hex("00A4040006F043414D505553"), TestProtocol.hex("00A4040007F043414D50555301")})
            assertArrayEquals(TestProtocol.hex("6D00"), TestProtocol.respond(invalid));
        for (int size = 0; size < TestProtocol.select().length; size++)
            assertArrayEquals(TestProtocol.hex("6D00"), TestProtocol.respond(Arrays.copyOf(TestProtocol.select(), size)));
    }
    @Test public void rejectsMalformedHex() {
        for (String invalid : new String[]{null, "0", "GG", "0?"}) {
            assertThrows(IllegalArgumentException.class, () -> TestProtocol.hex(invalid));
        }
    }
    @Test public void separatesClassicFromIsoDep() {
        assertTrue(CardTechnology.describe(new String[]{"android.nfc.tech.NfcA", "android.nfc.tech.MifareClassic"}).contains("無法模擬"));
        assertTrue(CardTechnology.describe(new String[]{"android.nfc.tech.NfcA", "android.nfc.tech.IsoDep"}).contains("待發行方"));
        assertTrue(CardTechnology.describe(new String[]{"android.nfc.tech.NfcA"}).contains("尚未成立"));
        assertTrue(CardTechnology.describe(new String[]{}).contains("未知"));
    }
}
