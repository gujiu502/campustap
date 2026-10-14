/* Adapted from AOSP CardEmulation launcher, Copyright 2013 AOSP, Apache-2.0. */
package com.example.android.cardemulation;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    // shortcut: Traditional Chinese only; use string resources when adding another language.
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NfcAdapter adapter;
    private LinearLayout content;
    private TextView capability, card, readerState;
    private volatile boolean scanning = false;
    private volatile boolean testReader = false;
    private boolean initialProbe = true;
    private final Runnable timeout = () -> stopScan("等待逾時；請調整卡片位置後重新偵測");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = NfcAdapter.getDefaultAdapter(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(246, 248, 250));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        text("CampusTap", 30, true, 0xFF24292F);
        text("學生證與 NFC 診斷", 16, false, 0xFF57606A);
        text("門禁尚未開通", 23, true, 0xFFBC4C00);
        text("需相容的系統門卡或校方核發手機憑證。讀到卡片不代表可用手機開門。", 16, false, 0xFF24292F);
        capability = text("", 16, false, 0xFF24292F);
        button("NFC 系統設定", () -> openSettings(Settings.ACTION_NFC_SETTINGS));
        button("目前付款服務設定", () -> openSettings(Settings.ACTION_NFC_PAYMENT_SETTINGS));
        text("學生證技術類型", 20, true, 0xFF24292F);
        card = text(getSharedPreferences("diagnostics", MODE_PRIVATE).getString("cardSummary", "尚未偵測"), 16, false, 0xFF24292F);
        readerState = text("只讀技術資訊；不保存完整 UID、不讀餘額、不驗證密鑰。", 14, false, 0xFF57606A);
        button("只讀偵測學生證（30 秒）", () -> startScan(false));
        text("自有 HCE 測試", 20, true, 0xFF24292F);
        text("手機等待另一台讀卡器選取測試服務。測試回應不含門禁或票證資料；學生證不能充當讀卡器。", 16, false, 0xFF24292F);
        button("測試另一部 CampusTap 手機", () -> {
            new android.app.AlertDialog.Builder(this).setTitle("需要第二部手機")
                    .setMessage("請移開學生證，僅靠近已安裝 CampusTap 的自有測試手機。這不會測試宿舍門禁。")
                    .setNegativeButton("取消", null).setPositiveButton("開始", (d, w) -> startScan(true)).show();
        });
        text("悠遊卡", 20, true, 0xFF24292F);
        text("實體卡管理與嗶乘車：由悠遊付辦理。嗶乘車須打開畫面並解鎖；原學生證餘額不會移轉到此 App。", 16, false, 0xFF24292F);
        button("開啟官方悠遊付", this::openEasyWallet);
        button("分享去識別化診斷", this::shareDiagnostics);
        button("清除本 App 診斷資料", () -> {
            for (String name : new String[]{"card_probe.json", "capability.json", "reader_test.json"}) {
                if (getFileStreamPath(name).exists() && !deleteFile(name)) {
                    Toast.makeText(this, "無法清除診斷檔案", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            getSharedPreferences("diagnostics", MODE_PRIVATE).edit().clear().apply();
            card.setText("尚未偵測");
        });
    }
    @Override protected void onResume() {
        super.onResume();
        refreshCapabilities();
        if (initialProbe) {
            initialProbe = false;
            if (adapter != null && adapter.isEnabled()) startScan(false);
        }
    }
    @Override protected void onPause() {
        stopScan("偵測已停止；離開前景時恢復正常 NFC 路由");
        super.onPause();
    }
    private void refreshCapabilities() {
        try {
            JSONObject report = capabilityReport();
            save("capability.json", report);
            capability.setText("NFC：" + (adapter == null ? "不支援" : adapter.isEnabled() ? "已開啟" : "已關閉")
                    + "　HCE：" + (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) ? "支援" : "不支援")
                    + "\n系統門卡組件：" + (hasPackage("com.miui.tsmclient") ? "已安裝，待驗證" : "未找到")
                    + "\nAndroid " + Build.VERSION.RELEASE + " · " + Build.MODEL);
        } catch (Exception e) { showError("設備報告儲存失敗", e); }
    }
    private JSONObject capabilityReport() throws Exception {
        RoleManager roles = getSystemService(RoleManager.class);
        return new JSONObject().put("model", Build.MODEL).put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT).put("rom", Build.VERSION.INCREMENTAL)
                .put("nfcPresent", adapter != null).put("nfcEnabled", adapter != null && adapter.isEnabled())
                .put("hceSupported", getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION))
                .put("walletRoleAvailable", Build.VERSION.SDK_INT >= 35 && roles != null && roles.isRoleAvailable(RoleManager.ROLE_WALLET))
                .put("oemDoorCardComponentInstalled", hasPackage("com.miui.tsmclient"))
                .put("easyWalletInstalled", hasPackage("com.easycard.wallet"))
                .put("doorUnlockStatus", "BLOCKED_NO_AUTHORIZED_MOBILE_CREDENTIAL")
                .put("testApduAt", getSharedPreferences("diagnostics", MODE_PRIVATE).getLong("testApduAt", 0));
    }
    private boolean hasPackage(String name) {
        try { getPackageManager().getPackageInfo(name, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    private void startScan(boolean readerTest) {
        if (adapter == null || !adapter.isEnabled()) { openSettings(Settings.ACTION_NFC_SETTINGS); return; }
        stopScan("");
        testReader = readerTest;
        scanning = true;
        try {
            adapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                    | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
            readerState.setText(readerTest ? "等待另一部 CampusTap 測試手機…" : "請將學生證靠近手機背面 NFC 區域；只讀偵測中…");
            handler.postDelayed(timeout, 30_000);
        } catch (RuntimeException e) { stopScan("無法開始偵測"); showError("NFC 偵測失敗", e); }
    }
    private void stopScan(String message) {
        scanning = false;
        handler.removeCallbacks(timeout);
        if (adapter != null) {
            try { adapter.disableReaderMode(this); }
            catch (RuntimeException e) { if (readerState != null) readerState.setText("NFC 路由恢復失敗；請關閉並重開 NFC"); return; }
        }
        if (readerState != null) readerState.setText(message);
    }
    @Override public void onTagDiscovered(Tag tag) {
        if (!scanning) return;
        boolean readerTest = testReader;
        scanning = false;
        try {
            JSONObject report = new JSONObject().put("observedAt", System.currentTimeMillis())
                    .put("technologies", new JSONArray(Arrays.asList(tag.getTechList())))
                    .put("uidByteLength", tag.getId().length).put("uidStored", false)
                    .put("cardContentsRead", false).put("cardModified", false);
            NfcA nfca = NfcA.get(tag);
            if (nfca != null) report.put("sak", nfca.getSak() & 0xFF)
                    .put("atqa", Arrays.toString(nfca.getAtqa()));
            String summary = CardTechnology.describe(tag.getTechList());
            if (readerTest) {
                IsoDep iso = IsoDep.get(tag);
                if (iso == null) throw new IllegalArgumentException("測試對象未提供 ISO-DEP");
                try {
                    iso.connect(); iso.setTimeout(2000);
                    byte[] response = iso.transceive(TestProtocol.select());
                    boolean pass = Arrays.equals(response, TestProtocol.respond(TestProtocol.select()));
                    report.put("syntheticHceTest", pass ? "PASS" : "FAIL");
                    summary = pass ? "自有 HCE 往返成功；門禁尚未驗證" : "自有 HCE 回應不符合預期";
                } finally { iso.close(); }
                save("reader_test.json", report);
            } else {
                report.put("classification", summary);
                save("card_probe.json", report);
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("cardSummary", summary).apply();
            }
            String result = summary;
            runOnUiThread(() -> { card.setText(result); stopScan("偵測結束；已恢復正常 NFC 路由"); });
        } catch (Exception e) { runOnUiThread(() -> { stopScan("偵測失敗；請移開卡片後重試"); showError("NFC 偵測失敗", e); }); }
    }
    private void save(String name, JSONObject report) throws Exception {
        try (FileOutputStream out = openFileOutput(name, MODE_PRIVATE)) {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException missing) { showError("找不到系統設定", missing); }
        }
    }
    private void openEasyWallet() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.easycard.wallet");
        if (launch == null) { Toast.makeText(this, "請從官方商店安裝悠遊付", Toast.LENGTH_LONG).show(); return; }
        try { startActivity(launch); } catch (ActivityNotFoundException e) { showError("無法開啟悠遊付", e); }
    }
    private void shareDiagnostics() {
        try {
            JSONObject report = capabilityReport();
            report.put("cardSummary", getSharedPreferences("diagnostics", MODE_PRIVATE).getString("cardSummary", "NOT_TESTED"));
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report.toString(2)), "分享診斷"));
        } catch (Exception e) { showError("分享診斷失敗", e); }
    }
    private void showError(String message, Exception e) {
        Toast.makeText(this, message + "（" + e.getClass().getSimpleName() + "）", Toast.LENGTH_LONG).show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(12), 0, dp(8));
        content.addView(view);
        return view;
    }
    private void button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
}
