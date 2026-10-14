/* Adapted from AOSP CardEmulation, Copyright 2013 AOSP, Apache-2.0. */
package com.example.android.cardemulation;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class CardService extends HostApduService {
    @Override public byte[] processCommandApdu(byte[] command, Bundle extras) {
        byte[] response = TestProtocol.respond(command);
        if (response.length > 2) {
            getSharedPreferences("diagnostics", MODE_PRIVATE).edit()
                    .putLong("testApduAt", System.currentTimeMillis()).apply();
        }
        return response;
    }
    @Override public void onDeactivated(int reason) { }
}
