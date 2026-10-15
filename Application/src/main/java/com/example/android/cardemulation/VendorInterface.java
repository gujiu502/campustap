package com.example.android.cardemulation;

/** Inspect method availability without constructing the vendor adapter or changing NFC state. */
final class VendorInterface {
    static final String CLASS_NAME = "com.st.android.nfc_extensions.NfcAdapterStExtensions";
    static boolean compatible(Class<?> type) {
        if (type == null || !java.lang.reflect.Modifier.isPublic(type.getModifiers())) return false;
        try {
            type.getConstructor();
            type.getMethod("programHceParameters", boolean.class, byte.class, byte.class, byte.class, byte[].class, byte.class, byte[].class);
            type.getMethod("sendPropSetConfig", int.class, int.class, byte[].class);
            return type.getMethod("sendPropGetConfig", int.class, int.class).getReturnType() == byte[].class
                    && type.getMethod("getNciConfig", int.class).getReturnType() == byte[].class;
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) { return false; }
    }
}
