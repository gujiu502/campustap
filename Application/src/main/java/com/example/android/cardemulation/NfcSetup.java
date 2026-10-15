package com.example.android.cardemulation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.widget.Toast;

/** Basic NFC is sufficient for card management; OEM simulation has a separate privileged check. */
final class NfcSetup {
    static boolean hasBasicAuthorization(Context context) {
        return context.checkSelfPermission("android.permission.NFC") == PackageManager.PERMISSION_GRANTED;
    }
    static VendorFailure authorizationFailure(Context context) {
        return VendorFailure.authorizationFailure(hasBasicAuthorization(context), VendorCardController.hasSystemPermission(context));
    }
    static boolean hasAuthorization(Context context) { return authorizationFailure(context) == null; }
    static String summary(Activity activity) {
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(activity);
        String simulation = !VendorCardController.hasVendorInterface() ? "系統未提供相容介面"
                : VendorCardController.hasSystemPermission(activity) ? "可嘗試啟用" : "未取得系統授權";
        return "NFC：" + (nfc == null ? "不支援" : nfc.isEnabled() ? "已開啟" : "未開啟") + "\n卡片模擬：" + simulation;
    }
    static void openSettings(Activity activity) {
        try { activity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
        catch (android.content.ActivityNotFoundException unavailable) { Toast.makeText(activity, "請手動開啟手機設定中的 NFC", Toast.LENGTH_LONG).show(); }
    }
}
