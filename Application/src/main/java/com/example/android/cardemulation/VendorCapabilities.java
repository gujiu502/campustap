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
        Class<?> api = oplusFactoryType(type);
        return oplusIdentityApi(api) ? api : null;
    }
    static Class<?> oplusFactoryType(Class<?> type) {
        if (type == null) return null;
        try {
            Method factory = type.getMethod("getVendorNfcAdapter");
            return Modifier.isStatic(factory.getModifiers()) ? factory.getReturnType() : null;
        } catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) { return null; }
    }
    static java.util.Map<String, String> oplusMethodStatus(Class<?> type) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("setHceTypeAConfig", methodStatus(type, "setHceTypeAConfig", boolean.class,
                boolean.class, byte[].class, byte[].class, byte[].class));
        result.put("getHceTypeAUid", methodStatus(type, "getHceTypeAUid", String.class));
        result.put("hasFeature", methodStatus(type, "hasFeature", boolean.class, String.class));
        return result;
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
        return "MATCH".equals(methodStatus(type, name, result, args));
    }
    private static String methodStatus(Class<?> type, String name, Class<?> result, Class<?>... args) {
        if (type == null) return "CLASS_UNAVAILABLE";
        if (!Modifier.isPublic(type.getModifiers())) return "CLASS_NOT_PUBLIC";
        try {
            Method method = type.getMethod(name, args);
            if (Modifier.isStatic(method.getModifiers())) return "STATIC_METHOD";
            return result == method.getReturnType() ? "MATCH" : "RETURN_TYPE_MISMATCH";
        } catch (NoSuchMethodException unavailable) { return "MISSING_OR_FILTERED"; }
        catch (SecurityException denied) { return "ACCESS_DENIED"; }
        catch (LinkageError unavailable) { return "LINKAGE_UNAVAILABLE"; }
    }
    static String unavailableCode(boolean st, boolean oplus) {
        return st || oplus ? "" : "VENDOR_INTERFACE_UNAVAILABLE";
    }
    static boolean useOplus(boolean st, boolean oplus, boolean stPermission) {
        return oplus && (!st || !stPermission);
    }
    static String backendCandidate(boolean st, boolean oplus, boolean stPermission) {
        if (useOplus(st, oplus, stPermission)) return "OPLUS_DIRECT_EXPERIMENTAL";
        return st ? stPermission ? "ST" : "ST_PERMISSION_REQUIRED" : "NONE";
    }
    static String chipIdentifier(String raw) {
        if (raw == null || raw.length() > 24) return "UNKNOWN";
        String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
        // Exact labels returned by the inspected OEM chip API; never export arbitrary vendor text.
        switch (value) {
            case "SN220U": case "SN220P": case "SN220F": case "GSN11": case "THN31": case "SN220T":
            case "PN560": case "PN557": case "ST54J": case "ST54H": case "ST21H":
            case "SN100U": case "SN100T": case "NQ310": case "NQ330": return value;
            default: return "UNKNOWN";
        }
    }
}
