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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    // shortcut: Traditional Chinese only; use string resources when adding another language.
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NfcAdapter adapter;
    private LinearLayout content;
    private TextView capability, card, readerState;
    private TextView accessState;
    private VendorCardController vendorController;
    private final ExecutorService vendorWorker = Executors.newSingleThreadExecutor();
    private volatile boolean resumed;
    private final Runnable vendorTimeout = this::stopVendorTest;
    private volatile boolean scanning = false;
    private volatile boolean testReader = false;
    private boolean initialProbe = true;
    private final Runnable timeout = () -> stopScan("等待逾時；請調整卡片位置後重新偵測");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = NfcAdapter.getDefaultAdapter(this);
        vendorController = new VendorCardController(this);
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
        accessState = text("門禁待實測", 23, true, 0xFFBC4C00);
        String lastFailure = getSharedPreferences("diagnostics", MODE_PRIVATE).getString("vendorFailure", "");
        if (!lastFailure.isEmpty()) accessState.setText(lastFailure);
        text("本機廠商接口測試：只支援卡號識別，不支援 MIFARE 密鑰認證。能否開門須現場驗證。", 16, false, 0xFF24292F);
        capability = text("", 16, false, 0xFF24292F);
        button("NFC 系統設定", () -> openSettings(Settings.ACTION_NFC_SETTINGS));
        button("目前付款服務設定", () -> openSettings(Settings.ACTION_NFC_PAYMENT_SETTINGS));
        text("學生證技術類型", 20, true, 0xFF24292F);
        card = text(getSharedPreferences("diagnostics", MODE_PRIVATE).getString("cardSummary", "尚未偵測"), 16, false, 0xFF24292F);
        readerState = text("只讀識別參數；卡號加密保存在本 App，不讀餘額、不讀密鑰。", 14, false, 0xFF57606A);
        button("只讀偵測學生證（30 秒）", () -> startScan(false));
        button("啟用手機門禁測試（2 分鐘）", this::confirmVendorTest);
        button("停止測試並恢復 NFC", this::stopVendorTest);
        button("刪除已錄入的門卡參數", () -> vendorWorker.execute(() -> {
            try { vendorController.forgetProfile(); runOnUiThread(() -> accessState.setText("已刪除門卡參數；未啟用測試")); }
            catch (Exception e) { runOnUiThread(() -> showError("刪除或恢復失敗", e)); }
        }));
        text("自有 HCE 測試", 20, true, 0xFF24292F);
        text("手機等待另一台讀卡器選取測試服務。測試回應不含門禁或票證資料；學生證不能充當讀卡器。", 16, false, 0xFF24292F);
        button("測試另一部 CampusTap 手機", () -> {
            new android.app.AlertDialog.Builder(this).setTitle("需要第二部手機")
                    .setMessage("請移開學生證，僅靠近已安裝 CampusTap 的自有測試手機。這不會測試宿舍門禁。")
                    .setNegativeButton("取消", null).setPositiveButton("開始", (d, w) -> startScan(true)).show();
        });
        button("分享去識別化診斷", this::shareDiagnostics);
        button("清除本 App 診斷資料", () -> {
            for (String name : new String[]{"card_probe.json", "capability.json", "reader_test.json", "vendor_probe.json", "vendor_test_enable.json", "vendor_test_restore.json", "vendor_test_failure.json"}) {
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
        resumed = true;
        refreshCapabilities();
        if (vendorController.recoveryRequired()) {
            initialProbe = false;
            stopVendorTest();
        }
        if (getIntent().getBooleanExtra("accessTest", false)) {
            getIntent().removeExtra("accessTest");
            initialProbe = false;
            handler.post(() -> { if (resumed && !vendorController.recoveryRequired()) confirmVendorTest(); });
        }
        if (initialProbe) {
            initialProbe = false;
            if (adapter != null && adapter.isEnabled()) startScan(false);
        }
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(vendorTimeout);
        stopScan("偵測已停止；離開前景時恢復正常 NFC 路由");
        stopVendorTest();
        super.onPause();
    }
    @Override protected void onDestroy() {
        vendorWorker.shutdown();
        super.onDestroy();
    }
    private void confirmVendorTest() {
        if (!vendorController.hasProfile()) {
            Toast.makeText(this, "請先只讀感應本人學生證，錄入識別參數", Toast.LENGTH_LONG).show();
            return;
        }
        new android.app.AlertDialog.Builder(this).setTitle("手機單獨測試門禁")
                .setMessage("先將實體學生證移離手機至少 20 公分，再刷本人有權使用的門禁。測試期間不要使用 NFC 付款；停止、離開畫面或 2 分鐘後會嘗試並核對恢復。若 App 被強制停止或系統殺掉，請重新開啟並按停止恢復，通過核對後再使用 NFC 付款。這不能複製密鑰或悠遊卡支付功能。")
                .setNegativeButton("取消", null).setPositiveButton("啟用測試", (d, w) -> enableVendorTest()).show();
    }
    private void enableVendorTest() {
        stopScan("讀卡模式已停止；準備門禁測試");
        accessState.setText("正在設定並核對控制器參數…");
        vendorWorker.execute(() -> {
            if (!resumed) return;
            try {
                JSONObject result = vendorController.enable();
                save("vendor_test_enable.json", result);
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("vendorFailure").apply();
                runOnUiThread(() -> {
                    if (!resumed) return;
                    accessState.setText("測試模式已啟用；開門結果待實測");
                    handler.removeCallbacks(vendorTimeout);
                    handler.postDelayed(vendorTimeout, 120_000);
                });
            } catch (Exception e) {
                // An I/O failure after enable must not leave an active test without its timeout.
                try { vendorController.restore(); } catch (Exception rollback) { e.addSuppressed(rollback); }
                String detail = recordVendorFailure("ENABLE_TEST", e);
                runOnUiThread(() -> {
                    accessState.setText(detail);
                    showError("門禁測試設定失敗", e);
                });
            }
        });
    }
    private void stopVendorTest() {
        handler.removeCallbacks(vendorTimeout);
        vendorWorker.execute(() -> {
            try {
                JSONObject result = vendorController.restore();
                if ("PASS".equals(result.optString("result"))) save("vendor_test_restore.json", result);
                if ("PASS".equals(result.optString("result"))) {
                    getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("vendorFailure").apply();
                    runOnUiThread(() -> accessState.setText("測試已停止；原配置恢復核對通過"));
                }
            } catch (Exception e) {
                String detail = recordVendorFailure("RESTORE", e);
                runOnUiThread(() -> { accessState.setText(detail); showError("恢復 NFC 失敗", e); });
            }
        });
    }
    private void refreshCapabilities() {
        try {
            JSONObject report = capabilityReport();
            save("capability.json", report);
            JSONObject vendor = VendorProbe.collect(this);
            save("vendor_probe.json", vendor);
            capability.setText("NFC：" + (adapter == null ? "不支援" : adapter.isEnabled() ? "已開啟" : "已關閉")
                    + "　HCE：" + (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) ? "支援" : "不支援")
                    + "\n系統門卡組件：" + (hasPackage("com.miui.tsmclient") ? "已安裝，待驗證" : "未找到")
                    + "\nAndroid " + Build.VERSION.RELEASE + " · " + Build.MODEL
                    + "\n廠商接口權限：" + vendor.getString("secureSettingsPermission"));
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
                .put("doorUnlockStatus", "NOT_TESTED").put("recoveryRequired", vendorController.recoveryRequired())
                .put("testApduAt", getSharedPreferences("diagnostics", MODE_PRIVATE).getLong("testApduAt", 0));
    }
    private boolean hasPackage(String name) {
        try { getPackageManager().getPackageInfo(name, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    private void startScan(boolean readerTest) {
        if (vendorController.recoveryRequired()) { stopVendorTest(); return; }
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
                    .put("uidByteLength", tag.getId().length).put("uidStoredEncrypted", false).put("uidPlaintextStored", false)
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
                if (nfca != null && Arrays.asList(tag.getTechList()).contains("android.nfc.tech.MifareClassic")) {
                    vendorController.saveProfile(new AccessCardProfile(tag.getId(), nfca.getAtqa(), nfca.getSak() & 255));
                    report.put("uidStoredEncrypted", true).put("uidPlaintextStored", false)
                            .put("storage", "AndroidKeyStore_AES_GCM").put("encryptedRoundtripVerified", true);
                    summary += "\n本人的識別參數已加密錄入；開門待實測";
                }
                report.put("classification", summary);
                save("card_probe.json", report);
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("cardSummary", summary).apply();
            }
            String result = summary;
            runOnUiThread(() -> { card.setText(result); stopScan("偵測結束；已退出讀卡模式"); });
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
    private void shareDiagnostics() {
        try {
            JSONObject report = capabilityReport();
            report.put("cardSummary", getSharedPreferences("diagnostics", MODE_PRIVATE).getString("cardSummary", "NOT_TESTED"));
            for (String name : new String[]{"vendor_probe.json", "vendor_test_enable.json", "vendor_test_restore.json", "vendor_test_failure.json"}) {
                if (getFileStreamPath(name).exists()) {
                    try (java.io.FileInputStream input = openFileInput(name)) {
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > 16_384) throw new IllegalStateException("Diagnostic report too large");
                            bytes.write(buffer, 0, count);
                        }
                        report.put(name, new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())));
                    }
                }
            }
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, report.toString(2)), "分享診斷"));
        } catch (Exception e) { showError("分享診斷失敗", e); }
    }
    private void showError(String message, Exception e) {
        VendorFailure failure = VendorFailure.at("DIAGNOSTIC", e);
        Toast.makeText(this, message + "（" + failure.errorType() + "）", Toast.LENGTH_LONG).show();
    }
    private String recordVendorFailure(String operation, Exception error) {
        VendorFailure failure = VendorFailure.at("SAVE_TEST_REPORT", error);
        boolean pending = vendorController.recoveryRequired();
        String detail = "門禁測試未完成：" + failure.description() + "\n步驟 " + failure.stage
                + " · " + failure.code + " · " + failure.errorType()
                + (pending ? "\n需恢復 NFC：保持 NFC 開啟並按停止，通過核對前不要付款" : "\n未有待恢復配置；開門尚未驗證");
        try {
            JSONObject report = new JSONObject().put("observedAt", System.currentTimeMillis()).put("operation", operation)
                    .put("result", "FAIL").put("stage", failure.stage).put("reasonCode", failure.code)
                    .put("errorType", failure.errorType()).put("recoveryRequired", pending)
                    .put("uidLogged", false).put("doorUnlockStatus", "NOT_TESTED");
            JSONArray recoveryErrors = new JSONArray();
            for (Throwable suppressed : error.getSuppressed()) {
                VendorFailure recovery = VendorFailure.at("RESTORE", suppressed);
                recoveryErrors.put(new JSONObject().put("stage", recovery.stage).put("reasonCode", recovery.code)
                        .put("errorType", recovery.errorType()));
            }
            save("vendor_test_failure.json", report.put("recoveryErrors", recoveryErrors));
        } catch (Exception saveFailure) { detail += "\n診斷檔儲存失敗，請保留畫面上的代碼"; }
        getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("vendorFailure", detail).apply();
        return detail;
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
