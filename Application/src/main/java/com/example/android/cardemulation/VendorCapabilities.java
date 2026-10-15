package com.example.android.cardemulation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Signatures alone never establish permission, service availability or RF compatibility. */
final class VendorCapabilities {
    static final String OPLUS = "com.vendor.nfc.VendorNfcAdapter";
    static final String NXP = "com.nxp.nfc.NxpNfcAdapter";
    static final String SAMSUNG = "com.samsung.android.nfc.adapter.SamsungNfcAdapter";
    static final String HUAWEI = "com.huawei.nfc.HwNfcAdapter";

    static boolean oplusIdentityApi(Class<?> type) {
        return instanceMethod(type, "setHceTypeAConfig", boolean.class,
                boolean.class, byte[].class, byte[].class, byte[].class)
                && instanceMethod(type, "getHceTypeAUid", String.class)
                && instanceMethod(type, "hasFeature", boolean.class, String.class);
    }
    static Class<?> oplusFactoryApi(Class<?> type) {
        if (type == null) return null;
        try {
            Method factory = type.getMethod("getVendorNfcAdapter");
            Class<?> api = factory.getReturnType();
            return Modifier.isStatic(factory.getModifiers()) && oplusIdentityApi(api) ? api : null;
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) { return null; }
    }
    static boolean stRoutes(Class<?> type, Class<?> contextType) {
        if (type == null) return false;
        try {
            Method factory = type.getMethod("getDefaultAdapter", contextType);
            return Modifier.isStatic(factory.getModifiers()) && type.isAssignableFrom(factory.getReturnType())
                    && instanceMethod(type, "getUserDefaultRoutes", java.util.Map.class);
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) { return false; }
    }
    private static boolean instanceMethod(Class<?> type, String name, Class<?> result, Class<?>... args) {
        if (type == null || !Modifier.isPublic(type.getModifiers())) return false;
        try {
            Method method = type.getMethod(name, args);
            return !Modifier.isStatic(method.getModifiers()) && result == method.getReturnType();
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) { return false; }
    }
    static String unavailableCode(boolean st, boolean oplus) {
        return st || oplus ? "" : "VENDOR_INTERFACE_UNAVAILABLE";
    }
    static boolean useOplus(boolean st, boolean oplus, boolean stPermission) {
        return oplus && (!st || !stPermission);
    }
}
