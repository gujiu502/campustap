package com.example.android.cardemulation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Protected OEM access requires a separate grant on each phone; a runtime dialog cannot grant it. */
final class NfcSetup {
    static final String GRANT_COMMAND = "adb -s SERIAL shell pm grant tw.campustap.diagnostics android.permission.WRITE_SECURE_SETTINGS";
    static String summary(Activity activity) {
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(activity);
        return "NFC：" + (nfc == null ? "不支援" : nfc.isEnabled() ? "已開啟" : "未開啟")
                + "\n系統設定授權：" + (VendorCardController.hasSystemPermission(activity) ? "已授權" : "未授權")
                + "\n裝置適配：" + (RadioConfiguration.verifiedDevice(Build.MODEL, Build.VERSION.INCREMENTAL) ? "已核驗" : "尚未支援")
                + "\n機型：" + Build.MODEL + "\n系統：" + Build.VERSION.INCREMENTAL;
    }
    static void show(Activity activity, Runnable recheck) {
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        String details = summary(activity)
                + "\n\n普通 NFC 權限已隨安裝提供。卡片模擬另外需要系統設定授權，Android 不提供普通授權彈窗，必須在這一台手機完成一次電腦授權。更新安裝通常會保留；重新安裝後需重新檢查。"
                + "\n\n1. 在手機開啟開發者選項與 USB 偵錯，連接自己的電腦並確認允許偵錯。Redmi 還需開啟「USB 偵錯（安全設定）」。"
                + "\n2. 電腦安裝 Android Platform Tools，執行 adb devices，將下方 SERIAL 換成目標手機的序列號，再執行指令。"
                + "\n3. 回到此處按「重新檢查」。不需要 Root，也不更改付款錢包。"
                + "\n\n目前卡片模擬僅核驗 Redmi " + RadioConfiguration.MODEL + " / " + RadioConfiguration.ROM
                + "。其他手機仍需適配；取得權限不代表它具備相同的控制器介面。";
        TextView text = new TextView(activity); text.setText(details); text.setTextSize(15); body.addView(text);
        TextView command = new TextView(activity); command.setText("\n" + GRANT_COMMAND); command.setTextSize(13);
        command.setTextIsSelectable(true); command.setTypeface(android.graphics.Typeface.MONOSPACE); body.addView(command);
        TextView copy = new TextView(activity); copy.setText("複製授權指令"); copy.setTextSize(16); copy.setTextColor(0xFF2C70EE);
        copy.setGravity(android.view.Gravity.CENTER); copy.setMinHeight(Math.round(48 * activity.getResources().getDisplayMetrics().density));
        copy.setOnClickListener(view -> {
            activity.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("CampusTap 授權", GRANT_COMMAND));
            Toast.makeText(activity, "已複製，請在電腦將 SERIAL 換成目標手機序列號", Toast.LENGTH_LONG).show();
        }); body.addView(copy);
        ScrollView scroll = new ScrollView(activity); scroll.addView(body);
        new AlertDialog.Builder(activity).setTitle("NFC 授權與相容性").setView(scroll)
                .setPositiveButton("重新檢查", (dialog, which) -> recheck.run())
                .setNeutralButton("NFC 設定", (dialog, which) -> {
                    try { activity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
                    catch (android.content.ActivityNotFoundException unavailable) { Toast.makeText(activity, "請手動開啟手機設定中的 NFC", Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton("關閉", null).show();
    }
}
