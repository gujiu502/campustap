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
                .put("doorUnlockStatus", "NOT_TESTED");
        JSONArray operations = new JSONArray();
        report.put("operationsAttempted", operations);
        try {
            Class<?> extension = Class.forName("com.st.android.nfc_extensions.NfcAdapterStExtensions");
            extension.getMethod("programHceParameters", boolean.class, byte.class, byte.class,
                    byte.class, byte[].class, byte.class, byte[].class);
            report.put("hceParameterMethodPresent", true);
            Object controller = extension.getConstructor().newInstance();
            operations.put("connectVendorService");
            if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
                operations.put("sendPropGetConfig(4,0)");
                byte[] configuration = (byte[]) extension.getMethod("sendPropGetConfig", int.class, int.class)
                        .invoke(controller, 4, 0);
                if (configuration != null && configuration.length > 26) {
                    report.put("controllerConfigurationBytes", configuration.length)
                            .put("configuredUidByteLength", configuration[20] & 0xFF)
                            .put("configuredSak", configuration[26] & 0xFF);
                }
                JSONObject nci = new JSONObject();
                JSONObject lengths = new JSONObject();
                for (int parameter : new int[]{0x30, 0x31, 0x32, 0x33, 0x58, 0x59, 0xFF}) {
                    operations.put("getNciConfig(" + Integer.toHexString(parameter) + ")");
                    byte[] value = (byte[]) extension.getMethod("getNciConfig", int.class).invoke(controller, parameter);
                    lengths.put(Integer.toHexString(parameter), value == null ? JSONObject.NULL : value.length);
                    if (parameter == 0x33 || parameter == 0x59) continue; // Do not export UID or historical card bytes.
                    JSONArray bytes = new JSONArray();
                    if (value != null) for (byte element : value) bytes.put(element & 0xFF);
                    nci.put(Integer.toHexString(parameter), value == null ? JSONObject.NULL : bytes);
                }
                report.put("nonIdentityNciParameters", nci).put("nciParameterLengths", lengths);
            }
            Class<?> settings = Class.forName("com.st.android.nfc_extensions.NfcSettingsAdapter");
            Object adapter = settings.getMethod("getDefaultAdapter", Context.class).invoke(null, context);
            operations.put("getDefaultAdapter");
            if (adapter == null) return report.put("readStatus", "SERVICE_UNAVAILABLE");
            Object routes = settings.getMethod("getUserDefaultRoutes").invoke(adapter);
            operations.put("getUserDefaultRoutes");
            if (!(routes instanceof Map<?, ?>)) return report.put("readStatus", "NO_ROUTE_RESULT");
            JSONObject defaults = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) routes).entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.startsWith("default_") && key.endsWith("_route"))
                    defaults.put(key, String.valueOf(entry.getValue()));
            }
            return report.put("userDefaultRoutes", defaults).put("readStatus", "PASS");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return report.put("readStatus", "API_UNAVAILABLE").put("errorType", e.getClass().getSimpleName());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            return report.put("readStatus", cause instanceof SecurityException ? "PERMISSION_DENIED" : "CALL_FAILED")
                    .put("errorType", cause == null ? e.getClass().getSimpleName() : cause.getClass().getSimpleName());
        } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
            return report.put("readStatus", "CALL_FAILED").put("errorType", e.getClass().getSimpleName());
        }
    }
}
