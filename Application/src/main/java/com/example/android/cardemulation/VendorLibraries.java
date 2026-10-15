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
        static final java.util.Map<String, ClassLoader> LOADERS = loaders();
        private static java.util.Map<String, ClassLoader> loaders() {
            java.util.Map<String, ClassLoader> result = new java.util.LinkedHashMap<>();
            ClassLoader parent = VendorLibraries.class.getClassLoader();
            result.put("SHARED", parent);
            for (String path : PATHS) {
                try {
                    File file = new File(path);
                    String canonical = file.getCanonicalPath();
                    if (file.isFile() && file.canRead() && !file.canWrite()
                            && (canonical.startsWith("/system_ext/") || canonical.startsWith("/system/system_ext/")))
                        result.put(path, new PathClassLoader(canonical, parent));
                } catch (java.io.IOException | SecurityException | LinkageError unavailable) { /* Continue with installed libraries. */ }
            }
            return result;
        }
    }
    static Class<?> find(String name) {
        for (ClassLoader loader : Libraries.LOADERS.values()) {
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
        for (ClassLoader loader : Libraries.LOADERS.values()) {
            Class<?> type = find(VendorInterface.CLASS_NAME, loader);
            if (VendorInterface.compatible(type) && VendorCapabilities.stRoutes(find(ST_SETTINGS, type.getClassLoader()), Context.class))
                return type;
        }
        return null;
    }
    static Class<?> oplusApi() {
        List<OplusConnection.Endpoint> endpoints = oplusEndpoints();
        return endpoints.isEmpty() ? null : endpoints.get(0).api;
    }
    static List<OplusConnection.Endpoint> oplusEndpoints() {
        List<OplusConnection.Endpoint> result = new ArrayList<>();
        if (VendorCapabilities.oplusIdentityApi(NfcAdapter.class))
            result.add(new OplusConnection.Endpoint("PLATFORM", NfcAdapter.class, null));
        java.util.Set<Class<?>> seen = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, ClassLoader> source : Libraries.LOADERS.entrySet()) {
            Class<?> factory = find(VendorCapabilities.OPLUS, source.getValue());
            Class<?> api = VendorCapabilities.oplusFactoryApi(factory);
            if (api == null || !seen.add(factory)) continue;
            try { result.add(new OplusConnection.Endpoint("ROM:" + source.getKey(), api, factory.getMethod("getVendorNfcAdapter"))); }
            catch (ReflectiveOperationException | SecurityException | LinkageError unavailable) { /* Try the next installed client. */ }
        }
        return result;
    }
    static String unavailableCode() {
        boolean st = stClass() != null;
        return VendorCapabilities.unavailableCode(st, !st && oplusApi() != null);
    }
    static JSONObject describe(Context context) throws org.json.JSONException {
        return new JSONObject().put("selectedBackendCandidate", VendorCardController.useOplus(context) ? "OPLUS_DIRECT_EXPERIMENTAL"
                        : stClass() != null ? "ST" : "NONE")
                .put("activeChipVerified", false)
                .put("oplusCandidateCount", oplusEndpoints().size())
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
            List<OplusConnection.Endpoint> endpoints = oplusEndpoints();
            if (endpoints.isEmpty()) return result.put("status", "API_UNAVAILABLE");
            NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
            if (nfc == null || !nfc.isEnabled()) return result.put("status", "NFC_UNAVAILABLE_OR_OFF");
            OplusConnection connection = OplusConnection.select(endpoints, nfc,
                    suffix -> android.provider.Settings.Global.getString(context.getContentResolver(), "hce_access_card_" + suffix), "", false);
            Object uid = connection.reportedUid();
            // No identity bytes or arbitrary vendor strings may enter the diagnostic report.
            String value = uid instanceof String ? (String) uid : "";
            boolean valid = "VALID".equals(OplusStateFormat.status(OplusStateFormat.Field.REPORTED_UID, value));
            return result.put("status", valid ? "IDENTITY_STATE_READABLE" : "NO_VALID_IDENTITY_STATE")
                    .put("uidByteLength", valid ? OplusStateFormat.parse(OplusStateFormat.Field.REPORTED_UID, value).length() / 2 : 0).put("rfVerified", false);
        } catch (Exception | LinkageError error) {
            try { result.put("status", "READ_FAILED").put("errorType", VendorFailure.at("READ_OPLUS", error).errorType()); }
            catch (org.json.JSONException ignored) { }
            return result;
        }
    }
}
