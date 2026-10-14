/* Adapted from AOSP CardEmulation CardService, Copyright 2013 AOSP, Apache-2.0. */
package com.example.android.cardemulation;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class TestProtocol {
    public static final String AID = "F043414D505553";
    public static byte[] select() { return hex("00A4040007" + AID); }
    public static byte[] respond(byte[] command) {
        byte[] select = select();
        boolean exact = Arrays.equals(select, command);
        boolean withLe = command != null && command.length == select.length + 1
                && command[command.length - 1] == 0
                && Arrays.equals(select, Arrays.copyOf(command, select.length));
        if (!exact && !withLe) return hex("6D00");
        byte[] message = "CAMPUS_TEST_ONLY".getBytes(StandardCharsets.US_ASCII);
        byte[] response = Arrays.copyOf(message, message.length + 2);
        response[message.length] = (byte) 0x90;
        return response;
    }
    public static byte[] hex(String value) {
        if (value == null || value.length() % 2 != 0) throw new IllegalArgumentException("Invalid hex length");
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hex character");
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
