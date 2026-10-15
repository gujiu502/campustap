package com.example.android.cardemulation;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Read only the two flags used by the inspected Android 13 NFC service; never write properties. */
final class OplusSystemState {
    enum Flag {
        OVERSEA("ro.oplus.connectivity.oversea"), ACCESS_CARD("ro.oplus.nfc.hce_accesscard");
        final String key;
        Flag(String key) { this.key = key; }
    }
    static String read(Flag flag) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", flag.key).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) return "READ_TIMEOUT";
            if (process.exitValue() != 0) return "READ_FAILED";
            byte[] buffer = new byte[129];
            int count = process.getInputStream().read(buffer);
            return count < 0 ? "EMPTY_OR_INACCESSIBLE" : count > 128 ? "UNRECOGNIZED"
                    : booleanStatus(new String(buffer, 0, count, StandardCharsets.US_ASCII));
        } catch (Exception | LinkageError error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return "READ_FAILED";
        } finally {
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                try { process.getInputStream().close(); } catch (java.io.IOException ignored) { }
                try { process.getErrorStream().close(); } catch (java.io.IOException ignored) { }
                try { process.getOutputStream().close(); } catch (java.io.IOException ignored) { }
            }
        }
    }
    static String booleanStatus(String value) {
        if (value == null || value.trim().isEmpty()) return "EMPTY_OR_INACCESSIBLE";
        switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1": case "true": case "y": case "yes": case "on": return "TRUE";
            case "0": case "false": case "n": case "no": case "off": return "FALSE";
            default: return "UNRECOGNIZED";
        }
    }
}
