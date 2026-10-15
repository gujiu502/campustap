package com.example.android.cardemulation;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** ADB-only, debug-only, read-only probe; no activity or unlocked screen required. */
public final class DeviceProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"tw.campustap.DEBUG_PROBE".equals(intent.getAction())) return;
        PendingResult result = goAsync();
        VendorCardController.WORKER.execute(() -> {
            try {
                JSONObject report = VendorProbe.collect(context);
                report.put("interactive", context.getSystemService(PowerManager.class).isInteractive())
                        .put("keyguardLocked", context.getSystemService(KeyguardManager.class).isKeyguardLocked());
                String active = VendorCardController.activeCardId(context);
                if (!active.isEmpty()) report.put("activeProfile", new VendorCardController(context, active).verifyActive());
                try (FileOutputStream output = context.openFileOutput("vendor_background_probe.json", Context.MODE_PRIVATE)) {
                    output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                result.setResultCode(-1); result.setResultData("PROBE_SAVED");
            } catch (Exception error) { result.setResultCode(1); result.setResultData(VendorFailure.at("PROBE", error).errorType()); }
            finally { result.finish(); }
        });
    }
}
