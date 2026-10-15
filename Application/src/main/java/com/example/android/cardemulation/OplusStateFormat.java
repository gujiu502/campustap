package com.example.android.cardemulation;

import java.util.Locale;

/** Only equivalent hex formatting is normalized; missing values never become defaults. */
final class OplusStateFormat {
    enum Field { MODE, ATQA, SAK, UID, REPORTED_UID }
    static String parse(Field field, String raw) throws VendorFailure {
        if (raw == null || raw.trim().isEmpty()) throw failure(field, "OPLUS_STATE_MISSING");
        if (raw.length() > 128) throw failure(field, "OPLUS_STATE_FORMAT_UNSUPPORTED");
        String value = raw.trim();
        if (field == Field.MODE) {
            if ("0".equals(value) || "1".equals(value)) return value;
            throw failure(field, "OPLUS_STATE_FORMAT_UNSUPPORTED");
        }
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (value.matches("[0-9a-fA-F]{2}(?:[-: ][0-9a-fA-F]{2})+")) value = value.replaceAll("[-: ]", "");
        int length = value.length();
        boolean validLength = field == Field.ATQA ? length == 4 : field == Field.SAK ? length == 2
                : length == 8 || length == 14 || length == 20;
        if (!validLength || !value.matches("[0-9a-fA-F]+")) throw failure(field, "OPLUS_STATE_FORMAT_UNSUPPORTED");
        return value.toLowerCase(Locale.ROOT);
    }
    static String status(Field field, String raw) {
        try { parse(field, raw); return "VALID"; }
        catch (VendorFailure error) { return error.code; }
    }
    private static VendorFailure failure(Field field, String code) { return new VendorFailure("OPLUS_READ_" + field.name(), code, null); }
}
