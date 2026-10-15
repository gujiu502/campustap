package com.example.android.cardemulation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Reapply the selected default when NFC becomes available; never change the payment role. */
public final class NfcStartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(intent.getAction())
                || intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1) != NfcAdapter.STATE_ON
                || !context.getSharedPreferences("nfc-state", Context.MODE_PRIVATE).getBoolean("autoActivate", true)) return;
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
        if (nfc == null || !nfc.isEnabled()) return;
        PendingResult pending = goAsync();
        VendorCardController.WORKER.execute(() -> {
            try {
                if (!context.getSharedPreferences("nfc-state", Context.MODE_PRIVATE).getBoolean("autoActivate", true)) return;
                CardStore cards = new CardStore(context);
                if (cards.defaultId.isEmpty()) return;
                JSONObject result = new VendorCardController(context, cards.defaultId).activate();
                try (FileOutputStream output = context.openFileOutput("vendor_test_enable.json", Context.MODE_PRIVATE)) {
                    output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception error) {
                VendorFailure failure = VendorFailure.at("AUTO_ACTIVATE", error);
                try (FileOutputStream output = context.openFileOutput("vendor_test_failure.json", Context.MODE_PRIVATE)) {
                    output.write(new JSONObject().put("operation", "AUTO_ACTIVATE").put("observedAt", System.currentTimeMillis())
                            .put("result", "FAIL").put("stage", failure.stage).put("reasonCode", failure.code)
                            .put("errorType", failure.errorType()).put("uidLogged", false).toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            } finally { pending.finish(); }
        });
    }
}
