package com.example.android.cardemulation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.util.Base64;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Device-specific card selection; no Classic authentication or payment implementation. */
final class VendorCardController {
    private static final String ROLLBACK = "radio_rollback.enc";
    static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();
    private final Context context;
    private final PrivateState state;
    private final String cardId, profileFile;
    VendorCardController(Context context) { this(context, "access"); }
    VendorCardController(Context context, String cardId) {
        this.context = context.getApplicationContext(); state = new PrivateState(context);
        this.cardId = cardId; profileFile = CardAppearance.profileFile(cardId);
    }
    boolean hasProfile() { return state.exists(profileFile); }
    boolean recoveryRequired() { return state.exists(ROLLBACK); }
    static String activeCardId(Context context) {
        if (!new PrivateState(context).exists(ROLLBACK)) return "";
        return context.getSharedPreferences("nfc-state", Context.MODE_PRIVATE).getString("activeCard", "");
    }
    void saveProfile(AccessCardProfile profile) throws Exception {
        JSONObject json = new JSONObject().put("uid", encode(profile.uid)).put("atqa", encode(profile.atqa)).put("sak", profile.sak & 255);
        synchronized (LOCK) {
            state.write(profileFile, json.toString().getBytes(StandardCharsets.UTF_8));
            AccessCardProfile saved = loadProfile();
            if (!Arrays.equals(profile.uid, saved.uid) || !Arrays.equals(profile.atqa, saved.atqa))
                throw new IllegalStateException("Encrypted profile verification failed");
        }
    }
    private AccessCardProfile loadProfile() throws Exception {
        JSONObject json = new JSONObject(new String(state.read(profileFile), StandardCharsets.UTF_8));
        return new AccessCardProfile(decode(json.getString("uid")), decode(json.getString("atqa")), json.getInt("sak"));
    }
    JSONObject activate() throws Exception {
        synchronized (LOCK) {
            JSONObject result = enable();
            try {
                if (!context.getSharedPreferences("nfc-state", Context.MODE_PRIVATE).edit().putString("activeCard", cardId).commit())
                    throw new IllegalStateException("Active selection could not be saved");
                result.put("operation", "ACTIVATE_CARD").put("cardId", cardId).put("foregroundOnly", false)
                        .put("timeoutSeconds", JSONObject.NULL).put("screenOffRfStatus", "NOT_TESTED");
                return result;
            } catch (Exception error) { try { restore(); } catch (Exception recovery) { error.addSuppressed(recovery); } throw error; }
        }
    }
    JSONObject verifyActive() throws Exception {
        synchronized (LOCK) {
            requirePermissionAndNfc();
            Object adapter = adapter();
            RadioConfiguration.requireEnabled(controller(adapter), readParameters(adapter), loadProfile());
            return new JSONObject().put("result", "PASS").put("cardId", cardId).put("uidLogged", false)
                    .put("doorUnlockStatus", "NOT_TESTED");
        }
    }
    JSONObject enable() throws Exception {
        synchronized (LOCK) {
            String stage = "RECOVER_PREVIOUS_TEST";
            boolean rollbackSaved = false;
            try {
                if (recoveryRequired()) restore();
                stage = "CHECK_PERMISSION_AND_DEVICE";
                requirePermissionAndNfc();
                stage = "LOAD_ENCRYPTED_PROFILE";
                if (!hasProfile()) throw new VendorFailure(stage, "PROFILE_REQUIRED", null);
                AccessCardProfile profile = loadProfile();
                stage = "CONNECT_VENDOR_SERVICE";
                Object adapter = adapter();
                stage = "SNAPSHOT_ORIGINAL_CONFIGURATION";
                JSONObject before = snapshot(adapter);
                byte[] raw = decode(before.getString("controller"));
                stage = "CHECK_BASELINE";
                RadioConfiguration.requireBaseline(raw, parameters(before.getJSONObject("nci")));
                stage = "SAVE_ENCRYPTED_ROLLBACK";
                byte[] recovery = before.toString().getBytes(StandardCharsets.UTF_8);
                state.write(ROLLBACK, recovery);
                if (!Arrays.equals(recovery, state.read(ROLLBACK)))
                    throw new VendorFailure(stage, "ROLLBACK_STORAGE_MISMATCH", null);
                rollbackSaved = true;
                stage = "PROGRAM_TEST_CONFIGURATION";
                JSONObject originalNci = before.getJSONObject("nci");
                program(adapter, true, profile.atqa[0], profile.atqa[1], profile.sak, profile.uid,
                        decode(originalNci.getString("58")), decode(originalNci.getString("59")));
                stage = "VERIFY_ENABLE";
                byte[] after = controller(adapter);
                RadioConfiguration.requireEnabled(after, readParameters(adapter), profile);
                return new JSONObject().put("observedAt", System.currentTimeMillis()).put("operation", "ENABLE_TEST")
                        .put("controllerReadback", "PASS").put("uidMatched", true).put("uidByteLength", 4)
                        .put("uidLogged", false).put("sak", profile.sak & 255).put("classicAuthentication", "NOT_IMPLEMENTED")
                        .put("doorUnlockStatus", "NOT_TESTED").put("foregroundOnly", true).put("timeoutSeconds", 120);
            } catch (Exception e) {
                VendorFailure failure = VendorFailure.at(stage, e);
                if (rollbackSaved) try { restore(); } catch (Exception rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }
    JSONObject restore() throws Exception {
        synchronized (LOCK) {
            if (!recoveryRequired()) return new JSONObject().put("operation", "RESTORE").put("result", "NO_ACTIVE_TEST");
            String stage = "RESTORE_CHECK_PERMISSION";
            try {
                requirePermissionAndNfc();
                stage = "LOAD_ENCRYPTED_ROLLBACK";
                JSONObject before = new JSONObject(new String(state.read(ROLLBACK), StandardCharsets.UTF_8));
                byte[] original = decode(before.getString("controller"));
                JSONObject parameters = before.getJSONObject("nci");
                // Validate the entire saved baseline before making any recovery write.
                java.util.Map<Integer, byte[]> saved = parameters(parameters);
                RadioConfiguration.requireBaseline(original, saved);
                stage = "RESTORE_CONNECT_SERVICE";
                Object adapter = adapter();
                stage = "RESTORE_PROGRAM_BASELINE";
                // The OEM false branch leaves NCI 0x32 unchanged; restore it in a discovery-paused program call first.
                byte[] rats = decode(parameters.getString("58")), history = decode(parameters.getString("59"));
                program(adapter, true, (byte) 4, (byte) 0, (byte) 32, new byte[0], rats, history);
                program(adapter, false, (byte) 4, (byte) 0, (byte) 32, new byte[0], rats, history);
                adapter.getClass().getMethod("sendPropSetConfig", int.class, int.class, byte[].class).invoke(adapter, 4, 0, original);
                stage = "VERIFY_RESTORE";
                boolean same = Arrays.equals(original, controller(adapter));
                for (int id : RadioConfiguration.PARAMETERS) {
                    same &= RadioConfiguration.restoredParameter(id, decode(parameters.getString(Integer.toHexString(id))), nci(adapter, id));
                }
                same &= before.getJSONObject("routes").toString().equals(routes().toString());
                if (!same) throw new VendorFailure(stage, "RESTORE_READBACK_MISMATCH", null);
                stage = "CLEAR_VERIFIED_ROLLBACK";
                state.delete(ROLLBACK);
                context.getSharedPreferences("nfc-state", Context.MODE_PRIVATE).edit().remove("activeCard").apply();
                return new JSONObject().put("observedAt", System.currentTimeMillis()).put("operation", "RESTORE")
                    .put("result", "PASS").put("controllerBytesRestored", true).put("nciParametersRestored", true)
                    .put("routesRestored", true).put("uidRestoreCheck", RadioConfiguration.dynamicUid(saved.get(0x33))
                            ? "DYNAMIC_MODE_AND_FORMAT" : "EXACT_BYTES").put("doorUnlockStatus", "NOT_TESTED");
            } catch (Exception e) { throw VendorFailure.at(stage, e); }
        }
    }
    void forgetProfile() throws Exception {
        synchronized (LOCK) {
            String active = activeCardId(context);
            if (cardId.equals(active) || (active.isEmpty() && recoveryRequired())) restore();
            state.delete(profileFile);
        }
    }
    private void requirePermissionAndNfc() throws VendorFailure {
        VendorFailure failure = readinessFailure(context);
        if (failure != null) throw failure;
    }
    static VendorFailure readinessFailure(Context context) {
        if (!RadioConfiguration.verifiedDevice(Build.MODEL, Build.VERSION.INCREMENTAL))
            return new VendorFailure("CHECK_DEVICE", "UNVERIFIED_DEVICE", null);
        if (!hasSystemPermission(context)) return new VendorFailure("CHECK_PERMISSION", "PERMISSION_REQUIRED", null);
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
        if (nfc == null) return new VendorFailure("CHECK_NFC", "NFC_UNAVAILABLE", null);
        return nfc.isEnabled() ? null : new VendorFailure("CHECK_NFC", "NFC_OFF", null);
    }
    static boolean hasSystemPermission(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }
    private Object adapter() throws Exception { return Class.forName("com.st.android.nfc_extensions.NfcAdapterStExtensions").getConstructor().newInstance(); }
    private byte[] controller(Object adapter) throws Exception {
        try {
            Object value = adapter.getClass().getMethod("sendPropGetConfig", int.class, int.class).invoke(adapter, 4, 0);
            if (!(value instanceof byte[])) throw new VendorFailure("READ_CONTROLLER", "NO_CONTROLLER_READBACK", null);
            return (byte[]) value;
        } catch (Exception error) { throw VendorFailure.at("READ_CONTROLLER", error); }
    }
    private byte[] nci(Object adapter, int id) throws Exception {
        String stage = "READ_NCI_" + Integer.toHexString(id).toUpperCase(java.util.Locale.ROOT);
        try {
            Object value = adapter.getClass().getMethod("getNciConfig", int.class).invoke(adapter, id);
            if (!(value instanceof byte[])) throw new VendorFailure(stage, "NO_NCI_READBACK", null);
            return (byte[]) value;
        } catch (Exception error) { throw VendorFailure.at(stage, error); }
    }
    private void program(Object adapter, boolean enabled, byte atqa0, byte atqa1, byte sak, byte[] uid,
            byte[] rats, byte[] history) throws Exception {
        Method method = adapter.getClass().getMethod("programHceParameters", boolean.class, byte.class, byte.class,
                byte.class, byte[].class, byte.class, byte[].class);
        method.invoke(adapter, enabled, atqa0, atqa1, sak, uid, rats.length == 0 ? (byte) 0 : rats[0], history);
    }
    private JSONObject routes() throws Exception {
        Class<?> settings = Class.forName("com.st.android.nfc_extensions.NfcSettingsAdapter");
        Object adapter = settings.getMethod("getDefaultAdapter", Context.class).invoke(null, context);
        if (adapter == null) throw new VendorFailure("READ_ROUTES", "SERVICE_UNAVAILABLE", null);
        Object value = settings.getMethod("getUserDefaultRoutes").invoke(adapter);
        if (!(value instanceof java.util.Map<?, ?>)) throw new IllegalStateException("No route snapshot");
        JSONObject json = new JSONObject();
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet()) sorted.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        for (java.util.Map.Entry<String, String> entry : sorted.entrySet()) json.put(entry.getKey(), entry.getValue());
        return json;
    }
    private JSONObject snapshot(Object adapter) throws Exception {
        JSONObject parameters = new JSONObject();
        for (int id : RadioConfiguration.PARAMETERS) parameters.put(Integer.toHexString(id), encode(nci(adapter, id)));
        return new JSONObject().put("controller", encode(controller(adapter))).put("nci", parameters).put("routes", routes());
    }
    private java.util.Map<Integer, byte[]> parameters(JSONObject json) throws Exception {
        java.util.Map<Integer, byte[]> values = new java.util.HashMap<>();
        for (int id : RadioConfiguration.PARAMETERS)
            if (json.has(Integer.toHexString(id))) values.put(id, decode(json.getString(Integer.toHexString(id))));
        return values;
    }
    private java.util.Map<Integer, byte[]> readParameters(Object adapter) throws Exception {
        java.util.Map<Integer, byte[]> values = new java.util.HashMap<>();
        for (int id : new int[]{0x30, 0x31, 0x32, 0x33}) values.put(id, nci(adapter, id));
        return values;
    }
    private static String encode(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value, Base64.NO_WRAP); }
}
