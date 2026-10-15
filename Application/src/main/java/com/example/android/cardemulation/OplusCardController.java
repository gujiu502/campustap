package com.example.android.cardemulation;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;

/** Direct typed OEM API; no shell, permission grant, payment-role or routing writes. */
final class OplusCardController {
    static final String ROLLBACK = "oplus_rollback.enc";
    private final Context context;
    private final PrivateState state;
    OplusCardController(Context context) { this.context = context; state = new PrivateState(context); }

    JSONObject compatibility() throws Exception {
        JSONObject report = new JSONObject().put("mutationAttempted", false).put("uidLogged", false)
                .put("recoveryPending", state.exists(ROLLBACK));
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
        if (nfc == null || !nfc.isEnabled()) return report.put("status", nfc == null ? "NFC_UNAVAILABLE" : "NFC_OFF");
        if (VendorLibraries.oplusApi() == null) return report.put("status", "API_UNAVAILABLE");
        try {
            Connection connection = new Connection();
            report.put("api", connection.api == NfcAdapter.class ? "PLATFORM" : "VENDOR_LIBRARY");
            try { report.put("featureEnabled", connection.api.getMethod("hasFeature", String.class).invoke(connection.adapter, "HCE_ACCESSCARD")); }
            catch (Exception | LinkageError error) { report.put("featureReadError", VendorFailure.at("OPLUS_FEATURE", error).errorType()); }
            JSONObject fields = new JSONObject();
            String storedUid = null, reportedUid = null;
            for (OplusStateFormat.Field field : OplusStateFormat.Field.values()) {
                JSONObject item = new JSONObject();
                try {
                    String raw;
                    if (field == OplusStateFormat.Field.REPORTED_UID) {
                        Object value = connection.api.getMethod("getHceTypeAUid").invoke(connection.adapter);
                        raw = value instanceof String ? (String) value : null;
                    } else raw = connection.setting(field.name().toLowerCase(java.util.Locale.ROOT));
                    item.put("status", OplusStateFormat.status(field, raw)).put("characters", raw == null ? 0 : Math.min(raw.length(), 129));
                    if (field == OplusStateFormat.Field.UID) storedUid = raw;
                    if (field == OplusStateFormat.Field.REPORTED_UID) reportedUid = raw;
                } catch (Exception | LinkageError error) { item.put("status", "READ_FAILED").put("errorType", VendorFailure.at("OPLUS_READ", error).errorType()); }
                fields.put(field.name(), item);
            }
            if ("VALID".equals(OplusStateFormat.status(OplusStateFormat.Field.UID, storedUid))
                    && "VALID".equals(OplusStateFormat.status(OplusStateFormat.Field.REPORTED_UID, reportedUid)))
                report.put("uidStatesMatch", OplusStateFormat.parse(OplusStateFormat.Field.UID, storedUid)
                        .equals(OplusStateFormat.parse(OplusStateFormat.Field.REPORTED_UID, reportedUid)));
            return report.put("status", "READ_ONLY_CHECK_COMPLETE").put("fields", fields);
        } catch (Exception | LinkageError error) {
            return report.put("status", "CONNECT_FAILED").put("errorType", VendorFailure.at("OPLUS_CONNECT", error).errorType());
        }
    }

    JSONObject enable(AccessCardProfile profile) throws Exception {
        try {
            Connection connection = new Connection();
            if (!Boolean.TRUE.equals(connection.api.getMethod("hasFeature", String.class).invoke(connection.adapter, "HCE_ACCESSCARD")))
                throw new VendorFailure("OPLUS_FEATURE", "OPLUS_FEATURE_UNAVAILABLE", null);
            transaction(connection).enable(profile);
            return report("ENABLE_TEST").put("uidByteLength", profile.uid.length).put("uidMatched", true)
                    .put("classicAuthentication", "NOT_IMPLEMENTED");
        } catch (Exception | LinkageError error) { throw VendorFailure.at("OPLUS_ENABLE", error); }
    }
    JSONObject verify(AccessCardProfile profile) throws Exception {
        try { transaction(new Connection()).verify(profile); return report("VERIFY_ACTIVE"); }
        catch (Exception | LinkageError error) { throw VendorFailure.at("OPLUS_VERIFY", error); }
    }
    JSONObject restore() throws Exception {
        try {
            transaction(new Connection()).restore();
            return report("RESTORE").put("restoreScope", "VENDOR_STATE_AND_DISABLED_OVERRIDE")
                    .put("controllerBytesRestored", false).put("routesWritten", false);
        } catch (Exception | LinkageError error) { throw VendorFailure.at("OPLUS_RESTORE", error); }
    }
    private JSONObject report(String operation) throws Exception {
        return new JSONObject().put("observedAt", System.currentTimeMillis()).put("operation", operation)
                .put("backend", "OPLUS_DIRECT_EXPERIMENTAL").put("result", "PASS").put("vendorStateReadback", "PASS")
                .put("controllerReadback", "NOT_AVAILABLE").put("uidLogged", false)
                .put("doorUnlockStatus", "NOT_TESTED").put("screenOffRfStatus", "NOT_TESTED");
    }
    private final class Connection implements OplusTransaction.Port {
        final Class<?> api;
        final Object adapter;
        Connection() throws Exception {
            api = VendorLibraries.oplusApi();
            if (api == null) throw new VendorFailure("OPLUS_CONNECT", "VENDOR_INTERFACE_UNAVAILABLE", null);
            adapter = api == NfcAdapter.class ? NfcAdapter.getDefaultAdapter(context)
                    : VendorLibraries.find(VendorCapabilities.OPLUS).getMethod("getVendorNfcAdapter").invoke(null);
            if (adapter == null) throw new VendorFailure("OPLUS_CONNECT", "SERVICE_UNAVAILABLE", null);
        }
        @Override public OplusTransaction.State read() throws Exception {
            Object uid = api.getMethod("getHceTypeAUid").invoke(adapter);
            return new OplusTransaction.State(setting("mode"), setting("atqa"), setting("sak"), setting("uid"),
                    uid instanceof String ? (String) uid : null);
        }
        private String setting(String suffix) { return Settings.Global.getString(context.getContentResolver(), "hce_access_card_" + suffix); }
        @Override public boolean set(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) throws Exception {
            return Boolean.TRUE.equals(api.getMethod("setHceTypeAConfig", boolean.class, byte[].class, byte[].class, byte[].class)
                    .invoke(adapter, enabled, atqa, sak, uid));
        }
    }
    private OplusTransaction transaction(Connection connection) {
        return new OplusTransaction(connection, new OplusTransaction.Recovery() {
            @Override public OplusTransaction.Snapshot load() throws Exception {
                if (!state.exists(ROLLBACK)) return null;
                JSONObject saved = new JSONObject(new String(state.read(ROLLBACK), StandardCharsets.UTF_8));
                if (saved.getInt("schema") != 1 || !"OPLUS_DIRECT".equals(saved.getString("backend")))
                    throw new VendorFailure("OPLUS_RECOVERY", "OPLUS_STATE_UNREADABLE", null);
                JSONObject before = saved.getJSONObject("before");
                JSONObject requested = saved.getJSONObject("requested");
                return new OplusTransaction.Snapshot(new OplusTransaction.State(before.getString("mode"), before.getString("atqa"),
                        before.getString("sak"), before.getString("uid"), before.getString("reportedUid")),
                        new AccessCardProfile(OplusTransaction.bytes(requested.getString("uid")),
                                OplusTransaction.bytes(requested.getString("atqa")), requested.getInt("sak")), saved.getBoolean("attempted"));
            }
            @Override public void save(OplusTransaction.Snapshot snapshot) throws Exception {
                OplusTransaction.State before = snapshot.before;
                AccessCardProfile profile = snapshot.requested;
                JSONObject json = new JSONObject().put("schema", 1).put("backend", "OPLUS_DIRECT").put("attempted", snapshot.attempted)
                        .put("before", new JSONObject().put("mode", before.mode).put("atqa", before.atqa).put("sak", before.sak)
                                .put("uid", before.uid).put("reportedUid", before.reportedUid))
                        .put("requested", new JSONObject().put("uid", OplusTransaction.hex(profile.uid))
                                .put("atqa", OplusTransaction.hex(profile.atqa)).put("sak", profile.sak & 255));
                byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
                state.write(ROLLBACK, bytes);
                if (!Arrays.equals(bytes, state.read(ROLLBACK))) throw new VendorFailure("OPLUS_RECOVERY", "ROLLBACK_STORAGE_MISMATCH", null);
            }
            @Override public void clear() throws Exception { state.delete(ROLLBACK); }
        });
    }
}
