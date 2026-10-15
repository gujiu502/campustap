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
        java.util.List<OplusConnection.Endpoint> endpoints = VendorLibraries.oplusEndpoints();
        if (endpoints.isEmpty()) return report.put("status", "API_UNAVAILABLE");
        org.json.JSONArray candidates = new org.json.JSONArray();
        for (OplusConnection.Endpoint endpoint : endpoints) {
            JSONObject candidate = new JSONObject().put("endpoint", endpoint.id);
            try { describe(OplusConnection.open(endpoint, nfc, this::setting), candidate); }
            catch (Exception | LinkageError error) {
                candidate.put("status", "CONNECT_FAILED").put("errorType", VendorFailure.at("OPLUS_CONNECT", error).errorType());
            }
            candidates.put(candidate);
        }
        report.put("candidates", candidates).put("status", "READ_ONLY_CHECK_COMPLETE");
        try { report.put("usableInactiveEndpoint", selectInactive().endpoint.id); }
        catch (Exception | LinkageError error) { report.put("preflightFailure", VendorFailure.at("OPLUS_PREFLIGHT", error).code); }
        return report;
    }
    private void describe(OplusConnection connection, JSONObject report) throws Exception {
        try {
            report.put("api", connection.endpoint.api == NfcAdapter.class ? "PLATFORM" : "VENDOR_LIBRARY");
            try { report.put("featureEnabled", connection.featureEnabled()); }
            catch (Exception | LinkageError error) { report.put("featureReadError", VendorFailure.at("OPLUS_FEATURE", error).errorType()); }
            JSONObject fields = new JSONObject();
            java.util.EnumMap<OplusStateFormat.Field, String> rawFields = new java.util.EnumMap<>(OplusStateFormat.Field.class);
            for (OplusStateFormat.Field field : OplusStateFormat.Field.values()) {
                JSONObject item = new JSONObject();
                try {
                    String raw;
                    if (field == OplusStateFormat.Field.REPORTED_UID) {
                        raw = connection.reportedUid();
                    } else raw = connection.setting(field.name().toLowerCase(java.util.Locale.ROOT));
                    item.put("status", OplusStateFormat.status(field, raw)).put("characters", raw == null ? 0 : Math.min(raw.length(), 129));
                    rawFields.put(field, raw);
                } catch (Exception | LinkageError error) { item.put("status", "READ_FAILED").put("errorType", VendorFailure.at("OPLUS_READ", error).errorType()); }
                fields.put(field.name(), item);
            }
            String storedUid = rawFields.get(OplusStateFormat.Field.UID), reportedUid = rawFields.get(OplusStateFormat.Field.REPORTED_UID);
            String mode = rawFields.get(OplusStateFormat.Field.MODE);
            if ("VALID".equals(OplusStateFormat.status(OplusStateFormat.Field.MODE, mode)))
                report.put("mode", OplusStateFormat.parse(OplusStateFormat.Field.MODE, mode));
            if (rawFields.size() == OplusStateFormat.Field.values().length) {
                try {
                    OplusTransaction.State current = new OplusTransaction.State(mode, rawFields.get(OplusStateFormat.Field.ATQA),
                            rawFields.get(OplusStateFormat.Field.SAK), storedUid, reportedUid);
                    report.put("uninitializedInactive", current.uninitializedInactive());
                } catch (VendorFailure error) { report.put("stateValidation", error.code); }
            }
            if ("VALID".equals(OplusStateFormat.status(OplusStateFormat.Field.UID, storedUid))
                    && "VALID".equals(OplusStateFormat.status(OplusStateFormat.Field.REPORTED_UID, reportedUid)))
                report.put("uidStatesMatch", OplusStateFormat.parse(OplusStateFormat.Field.UID, storedUid)
                        .equals(OplusStateFormat.parse(OplusStateFormat.Field.REPORTED_UID, reportedUid)));
            report.put("status", "READ_ONLY_CHECK_COMPLETE").put("fields", fields);
        } catch (Exception | LinkageError error) {
            report.put("status", "READ_FAILED").put("errorType", VendorFailure.at("OPLUS_READ", error).errorType());
        }
    }

    JSONObject enable(AccessCardProfile profile) throws Exception {
        try {
            if (state.exists(ROLLBACK)) restore();
            OplusConnection connection = selectInactive();
            boolean fromEmpty = transaction(connection).enable(profile);
            return report("ENABLE_TEST").put("endpoint", connection.endpoint.id).put("startedWithoutVendorIdentity", fromEmpty).put("uidByteLength", profile.uid.length).put("uidMatched", true)
                    .put("classicAuthentication", "NOT_IMPLEMENTED");
        } catch (Exception | LinkageError error) { throw VendorFailure.at("OPLUS_ENABLE", error); }
    }
    JSONObject verify(AccessCardProfile profile) throws Exception {
        try { transaction(recoveryConnection()).verify(profile); return report("VERIFY_ACTIVE"); }
        catch (Exception | LinkageError error) { throw VendorFailure.at("OPLUS_VERIFY", error); }
    }
    JSONObject restore() throws Exception {
        try {
            if (!state.exists(ROLLBACK)) return report("RESTORE").put("result", "NO_ACTIVE_TEST");
            transaction(recoveryConnection()).restore();
            return report("RESTORE").put("restoreScope", "DISABLED_OVERRIDE_AND_CHECKED_VENDOR_STATE")
                    .put("controllerBytesRestored", false).put("routesWritten", false);
        } catch (Exception | LinkageError error) { throw VendorFailure.at("OPLUS_RESTORE", error); }
    }
    private JSONObject report(String operation) throws Exception {
        return new JSONObject().put("observedAt", System.currentTimeMillis()).put("operation", operation)
                .put("backend", "OPLUS_DIRECT_EXPERIMENTAL").put("result", "PASS").put("vendorStateReadback", "PASS")
                .put("controllerReadback", "NOT_AVAILABLE").put("uidLogged", false)
                .put("doorUnlockStatus", "NOT_TESTED").put("screenOffRfStatus", "NOT_TESTED");
    }
    private String setting(String suffix) { return Settings.Global.getString(context.getContentResolver(), "hce_access_card_" + suffix); }
    private OplusConnection selectInactive() throws Exception {
        return OplusConnection.select(VendorLibraries.oplusEndpoints(), NfcAdapter.getDefaultAdapter(context), this::setting, "", true);
    }
    private String savedEndpoint(JSONObject saved) throws Exception {
        java.util.List<OplusConnection.Endpoint> endpoints = VendorLibraries.oplusEndpoints();
        return OplusConnection.recoveryEndpoint(saved.getInt("schema"), saved.optString("endpoint"),
                endpoints.isEmpty() ? "" : endpoints.get(0).id);
    }
    private OplusConnection recoveryConnection() throws Exception {
        JSONObject saved = new JSONObject(new String(state.read(ROLLBACK), StandardCharsets.UTF_8));
        return OplusConnection.select(VendorLibraries.oplusEndpoints(), NfcAdapter.getDefaultAdapter(context), this::setting, savedEndpoint(saved), false);
    }
    private OplusTransaction transaction(OplusConnection connection) {
        return new OplusTransaction(connection, new OplusTransaction.Recovery() {
            @Override public OplusTransaction.Snapshot load() throws Exception {
                if (!state.exists(ROLLBACK)) return null;
                JSONObject saved = new JSONObject(new String(state.read(ROLLBACK), StandardCharsets.UTF_8));
                if (!"OPLUS_DIRECT".equals(saved.getString("backend")) || !connection.endpoint.id.equals(savedEndpoint(saved)))
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
                JSONObject json = new JSONObject().put("schema", 2).put("backend", "OPLUS_DIRECT").put("endpoint", connection.endpoint.id).put("attempted", snapshot.attempted)
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
