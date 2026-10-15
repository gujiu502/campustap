package com.example.android.cardemulation;

import android.content.Context;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** Reads the installed OEM library; never invokes a setter or raw command. */
public final class VendorProbe {
    public static JSONObject collect(Context context) throws Exception {
        JSONObject report = new JSONObject().put("observedAt", System.currentTimeMillis())
                .put("secureSettingsPermission", context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                        == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                .put("mutationAttempted", false).put("rfParametersChanged", false)
                .put("doorUnlockStatus", "NOT_TESTED")
                .put("vendorCapabilities", VendorLibraries.describe(context))
                .put("oplusReadOnly", VendorLibraries.readOplusUidStatus(context));
        return readSt(context, report, true);
    }
    static JSONObject stCompatibility(Context context) throws Exception {
        JSONObject report = new JSONObject().put("mutationAttempted", false).put("uidLogged", false)
                .put("basicNfcPermission", NfcSetup.hasBasicAuthorization(context) ? "GRANTED" : "DENIED")
                .put("secureSettingsPermission", VendorCardController.hasSystemPermission(context) ? "GRANTED" : "DENIED")
                .put("writeBaselineStatus", "NOT_CHECKED");
        android.nfc.NfcAdapter nfc = android.nfc.NfcAdapter.getDefaultAdapter(context);
        if (nfc == null || !nfc.isEnabled()) return report.put("readStatus", nfc == null ? "NFC_UNAVAILABLE" : "NFC_OFF");
        return readSt(context, report, false);
    }
    private static JSONObject readSt(Context context, JSONObject report, boolean engineeringDetails) throws Exception {
        JSONArray operations = new JSONArray();
        report.put("operationsAttempted", operations);
        try {
            Class<?> extension = VendorLibraries.stClass();
            if (extension == null) return report.put("readStatus", "ST_API_UNAVAILABLE");
            extension.getMethod("programHceParameters", boolean.class, byte.class, byte.class,
                    byte.class, byte[].class, byte.class, byte[].class);
            report.put("hceParameterMethodPresent", true);
            report.put("readStage", "CONNECT_ST_SERVICE");
            operations.put("connectVendorService");
            Object controller = extension.getConstructor().newInstance();
            if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
                report.put("readStage", "READ_CONTROLLER");
                operations.put("sendPropGetConfig(4,0)");
                byte[] configuration = (byte[]) extension.getMethod("sendPropGetConfig", int.class, int.class)
                        .invoke(controller, 4, 0);
                if (configuration == null) return report.put("readStatus", "NO_CONTROLLER_READBACK");
                report.put("controllerConfigurationBytes", configuration.length);
                if (configuration.length > 26) {
                    report.put("configuredUidByteLength", configuration[20] & 0xFF)
                            .put("configuredSak", configuration[26] & 0xFF);
                }
                JSONObject nci = new JSONObject();
                JSONObject lengths = new JSONObject();
                java.util.Map<Integer, byte[]> parameters = new java.util.HashMap<>();
                for (int parameter : engineeringDetails ? new int[]{0x30, 0x31, 0x32, 0x33, 0x58, 0x59, 0xFF} : RadioConfiguration.PARAMETERS) {
                    report.put("readStage", "READ_NCI_" + Integer.toHexString(parameter));
                    operations.put("getNciConfig(" + Integer.toHexString(parameter) + ")");
                    byte[] value = (byte[]) extension.getMethod("getNciConfig", int.class).invoke(controller, parameter);
                    parameters.put(parameter, value);
                    lengths.put(Integer.toHexString(parameter), value == null ? JSONObject.NULL : value.length);
                    if (parameter == 0x33) report.put("nciUidHasRandomFormat", RadioConfiguration.dynamicUid(value));
                    if (!engineeringDetails || parameter == 0x33 || parameter == 0x59) continue; // Never export identity or history bytes.
                    JSONArray bytes = new JSONArray();
                    if (value != null) for (byte element : value) bytes.put(element & 0xFF);
                    nci.put(Integer.toHexString(parameter), value == null ? JSONObject.NULL : bytes);
                }
                if (engineeringDetails) report.put("nonIdentityNciParameters", nci);
                report.put("nciParameterLengths", lengths);
                try { RadioConfiguration.requireBaseline(configuration, parameters); report.put("writeBaselineStatus", "MATCH"); }
                catch (VendorFailure error) { report.put("writeBaselineStatus", error.code); }
            }
            Class<?> settings = Class.forName(VendorLibraries.ST_SETTINGS, true, extension.getClassLoader());
            report.put("readStage", "CONNECT_ST_SETTINGS");
            Object adapter = settings.getMethod("getDefaultAdapter", Context.class).invoke(null, context);
            operations.put("getDefaultAdapter");
            if (adapter == null) return report.put("readStatus", "SERVICE_UNAVAILABLE");
            report.put("readStage", "READ_ST_ROUTES");
            Object routes = settings.getMethod("getUserDefaultRoutes").invoke(adapter);
            operations.put("getUserDefaultRoutes");
            if (!(routes instanceof Map<?, ?>)) return report.put("readStatus", "NO_ROUTE_RESULT");
            JSONObject defaults = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) routes).entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.startsWith("default_") && key.endsWith("_route"))
                    defaults.put(key, String.valueOf(entry.getValue()));
            }
            if (engineeringDetails) report.put("userDefaultRoutes", defaults);
            return report.put("readStage", "COMPLETE")
                    .put("readStatus", VendorCardController.hasSystemPermission(context) ? "PASS" : "READS_COMPLETED_WITHOUT_WRITE_PERMISSION");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return report.put("readStatus", "API_UNAVAILABLE").put("errorType", e.getClass().getSimpleName());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            return report.put("readStatus", cause instanceof SecurityException ? "PERMISSION_DENIED" : "CALL_FAILED")
                    .put("errorType", cause == null ? e.getClass().getSimpleName() : cause.getClass().getSimpleName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return report.put("readStatus", e instanceof SecurityException ? "PERMISSION_DENIED" : "CALL_FAILED")
                    .put("errorType", e.getClass().getSimpleName());
        }
    }
}
