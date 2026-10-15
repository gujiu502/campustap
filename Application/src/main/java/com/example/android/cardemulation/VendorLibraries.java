package com.example.android.cardemulation;

import android.content.Context;
import android.nfc.NfcAdapter;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Only ROM-owned library locations; no downloaded, app-writable or user-selected code. */
final class VendorLibraries {
    static final String ST_SETTINGS = "com.st.android.nfc_extensions.NfcSettingsAdapter";
    // Oplus paths: docs/vendor-compatibility.md. Loading is discovery, never proof of support.
    private static final String[] PATHS = {
        "/system_ext/framework/nfcvendorlib.jar",
        "/system_ext/framework/oplusex/com.oplus.NfcNci/nfcvendorlib.jar",
        "/system_ext/framework/com.vendor.nfc.jar"
    };
    private static final class Libraries {
        static final List<ClassLoader> LOADERS = loaders();
        private static List<ClassLoader> loaders() {
            List<ClassLoader> result = new ArrayList<>();
            ClassLoader parent = VendorLibraries.class.getClassLoader();
            result.add(parent);
            for (String path : PATHS) {
                try {
                    File file = new File(path);
                    String canonical = file.getCanonicalPath();
                    if (file.isFile() && file.canRead() && !file.canWrite()
                            && (canonical.startsWith("/system_ext/") || canonical.startsWith("/system/system_ext/")))
                        result.add(new PathClassLoader(canonical, parent));
                } catch (java.io.IOException | SecurityException | LinkageError unavailable) { /* Continue with installed libraries. */ }
            }
            return result;
        }
    }
    static Class<?> find(String name) {
        for (ClassLoader loader : Libraries.LOADERS) {
            Class<?> type = find(name, loader);
            if (type != null) return type;
        }
        return null;
    }
    private static Class<?> find(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException | SecurityException | LinkageError unavailable) { return null; }
    }
    static Class<?> stClass() {
        for (ClassLoader loader : Libraries.LOADERS) {
            Class<?> type = find(VendorInterface.CLASS_NAME, loader);
            if (VendorInterface.compatible(type) && VendorCapabilities.stRoutes(find(ST_SETTINGS, type.getClassLoader()), Context.class))
                return type;
        }
        return null;
    }
    static Class<?> oplusApi() {
        if (VendorCapabilities.oplusIdentityApi(NfcAdapter.class)) return NfcAdapter.class;
        return VendorCapabilities.oplusFactoryApi(find(VendorCapabilities.OPLUS));
    }
    static String unavailableCode() {
        boolean st = stClass() != null;
        return VendorCapabilities.unavailableCode(st, !st && oplusApi() != null);
    }
    static JSONObject describe() throws org.json.JSONException {
        return new JSONObject().put("preferredBackendWithStPermission", stClass() != null ? "ST" : oplusApi() != null ? "OPLUS_DIRECT_EXPERIMENTAL" : "NONE")
                .put("st", new JSONObject().put("classPresent", find(VendorInterface.CLASS_NAME) != null)
                        .put("requiredMethodsPresent", stClass() != null).put("runtimeBaselineCheckRequired", true))
                .put("oplus", new JSONObject().put("classPresent", find(VendorCapabilities.OPLUS) != null)
                        .put("identityMethodsPresent", oplusApi() != null).put("writableBackendImplemented", true)
                        .put("experimental", true).put("readableInactiveBaselineRequired", true)
                        .put("controllerSnapshotAvailable", false).put("rfVerified", false))
                .put("nxp", discovery(VendorCapabilities.NXP, "NO_VERIFIED_UID_SNAPSHOT_RESTORE"))
                .put("samsung", discovery(VendorCapabilities.SAMSUNG, "ACCESSORY_API_IS_NOT_UID_EMULATION"))
                .put("huawei", discovery(VendorCapabilities.HUAWEI, "ROUTING_API_IS_NOT_UID_EMULATION"))
                .put("standardHce", new JSONObject().put("fixedUidApi", false))
                .put("detectionOnly", true).put("servicePermissionAndRfVerified", false).put("uidLogged", false);
    }
    private static JSONObject discovery(String name, String reason) throws org.json.JSONException {
        return new JSONObject().put("classPresent", find(name) != null).put("writableBackendImplemented", false).put("reason", reason);
    }
    static JSONObject readOplusUidStatus(Context context) {
        JSONObject result = new JSONObject();
        try {
            Class<?> api = oplusApi();
            if (api == null) return result.put("status", "API_UNAVAILABLE");
            NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
            if (nfc == null || !nfc.isEnabled()) return result.put("status", "NFC_UNAVAILABLE_OR_OFF");
            Object adapter = api == NfcAdapter.class ? nfc
                    : find(VendorCapabilities.OPLUS).getMethod("getVendorNfcAdapter").invoke(null);
            if (adapter == null) return result.put("status", "SERVICE_UNAVAILABLE");
            Object uid = api.getMethod("getHceTypeAUid").invoke(adapter);
            // No identity bytes or arbitrary vendor strings may enter the diagnostic report.
            String value = uid instanceof String ? (String) uid : "";
            boolean valid = value.matches("(?i)(?:[0-9a-f]{8}|[0-9a-f]{14}|[0-9a-f]{20})");
            return result.put("status", valid ? "IDENTITY_STATE_READABLE" : "NO_VALID_IDENTITY_STATE")
                    .put("uidByteLength", valid ? value.length() / 2 : 0).put("rfVerified", false);
        } catch (Exception | LinkageError error) {
            try { result.put("status", "READ_FAILED").put("errorType", VendorFailure.at("READ_OPLUS", error).errorType()); }
            catch (org.json.JSONException ignored) { }
            return result;
        }
    }
}
