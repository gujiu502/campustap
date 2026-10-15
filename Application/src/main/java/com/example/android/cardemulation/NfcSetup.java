package com.example.android.cardemulation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.widget.Toast;

/** Basic NFC for card management; simulation uses backend-specific preflight checks. */
final class NfcSetup {
    static boolean hasBasicAuthorization(Context context) {
        return context.checkSelfPermission("android.permission.NFC") == PackageManager.PERMISSION_GRANTED;
    }
    static VendorFailure authorizationFailure(Context context) {
        return VendorFailure.authorizationFailure(hasBasicAuthorization(context), VendorCardController.hasSystemPermission(context));
    }
    static String summary(Activity activity) {
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(activity);
        String unavailable = VendorLibraries.unavailableCode();
        String simulation = !unavailable.isEmpty() ? "尚無可安全啟用的介面"
                : VendorCardController.useOplus(activity) ? "可嘗試直接啟用（實驗性）"
                : VendorCardController.hasSystemPermission(activity) ? "可嘗試啟用" : "未取得系統授權";
        return "NFC：" + (nfc == null ? "不支援" : nfc.isEnabled() ? "已開啟" : "未開啟") + "\n卡片模擬：" + simulation;
    }
    static void openSettings(Activity activity) {
        try { activity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
        catch (android.content.ActivityNotFoundException unavailable) { Toast.makeText(activity, "請手動開啟手機設定中的 NFC", Toast.LENGTH_LONG).show(); }
    }
}
