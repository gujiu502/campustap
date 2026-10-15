package com.example.android.cardemulation;

import java.lang.reflect.Method;
import java.util.List;

/** Select with reads only; a saved transaction always reconnects to its original endpoint. */
final class OplusConnection implements OplusTransaction.Port {
    interface SettingsReader { String read(String suffix) throws Exception; }
    static final class Endpoint {
        final String id;
        final Class<?> api;
        final Method factory;
        Endpoint(String id, Class<?> api, Method factory) { this.id = id; this.api = api; this.factory = factory; }
    }
    final Endpoint endpoint;
    private final Object adapter;
    private final SettingsReader settings;
    private OplusConnection(Endpoint endpoint, Object platform, SettingsReader settings) throws Exception {
        this.endpoint = endpoint;
        this.settings = settings;
        adapter = endpoint.factory == null ? platform : endpoint.factory.invoke(null);
        if (adapter == null || !endpoint.api.isInstance(adapter)) throw failure("SERVICE_UNAVAILABLE");
    }
    static OplusConnection open(Endpoint endpoint, Object platform, SettingsReader settings) throws Exception {
        return new OplusConnection(endpoint, platform, settings);
    }
    static OplusConnection select(List<Endpoint> endpoints, Object platform, SettingsReader settings,
            String requiredId, boolean requireInactive) throws Exception {
        VendorFailure failure = null;
        for (Endpoint endpoint : endpoints) {
            if (!requiredId.isEmpty() && !requiredId.equals(endpoint.id)) continue;
            try {
                OplusConnection connection = open(endpoint, platform, settings);
                if (requireInactive) {
                    if (!connection.featureEnabled()) throw failure("OPLUS_FEATURE_UNAVAILABLE");
                    connection.read().requireBaseline();
                }
                return connection;
            } catch (Exception | LinkageError error) {
                VendorFailure next = VendorFailure.at("OPLUS_PREFLIGHT", error);
                if (failure != null && next != failure) next.addSuppressed(failure);
                failure = next;
            }
        }
        if (failure != null) throw failure;
        throw failure(requiredId.isEmpty() ? "VENDOR_INTERFACE_UNAVAILABLE" : "OPLUS_RECOVERY_INTERFACE_UNAVAILABLE");
    }
    static String recoveryEndpoint(int schema, String stored, String legacyDefault) throws VendorFailure {
        if (schema == 1 && !legacyDefault.isEmpty()) return legacyDefault;
        if (schema == 2 && stored != null && !stored.isEmpty()) return stored;
        throw failure("OPLUS_RECOVERY_INTERFACE_UNAVAILABLE");
    }
    boolean featureEnabled() throws Exception {
        return Boolean.TRUE.equals(endpoint.api.getMethod("hasFeature", String.class).invoke(adapter, "HCE_ACCESSCARD"));
    }
    String setting(String suffix) throws Exception { return settings.read(suffix); }
    String reportedUid() throws Exception {
        Object value = endpoint.api.getMethod("getHceTypeAUid").invoke(adapter);
        return value instanceof String ? (String) value : null;
    }
    @Override public OplusTransaction.State read() throws Exception {
        return new OplusTransaction.State(setting("mode"), setting("atqa"), setting("sak"), setting("uid"), reportedUid());
    }
    @Override public boolean set(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) throws Exception {
        return Boolean.TRUE.equals(endpoint.api.getMethod("setHceTypeAConfig", boolean.class, byte[].class, byte[].class, byte[].class)
                .invoke(adapter, enabled, atqa, sak, uid));
    }
    private static VendorFailure failure(String code) { return new VendorFailure("OPLUS_PREFLIGHT", code, null); }
}
