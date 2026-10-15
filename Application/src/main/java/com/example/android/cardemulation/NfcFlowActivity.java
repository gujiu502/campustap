package com.example.android.cardemulation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public enrollment and persistent card activation; engineering controls stay internal. */
public final class NfcFlowActivity extends Activity implements NfcAdapter.ReaderCallback {
    private static final int BLUE = 0xFF2C70EE, INK = 0xFF111827, MUTED = 0xFF697386;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = VendorCardController.WORKER;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private NfcAdapter nfc;
    private VendorCardController controller;
    private TextView title, detail, symbol;
    private Button primary, secondary;
    private String mode, cardType, cardId;
    private volatile boolean resumed;
    private boolean busy;
    private final Runnable readTimeout = () -> {
        stopReader(); status("尚未感應到卡片", "將卡片貼近手機背面 NFC 區域，調整位置後重試。", "重試讀卡", this::startRead);
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mode = getIntent().getStringExtra("mode");
        if (!"read".equals(mode) && !"restore".equals(mode)) mode = "activate";
        cardType = "student".equals(getIntent().getStringExtra("cardType")) ? "student" : "access";
        cardId = "read".equals(mode) ? (state == null ? java.util.UUID.randomUUID().toString() : state.getString("cardId"))
                : getIntent().getStringExtra("cardId");
        if (cardId == null) cardId = "access";
        controller = new VendorCardController(this, cardId); nfc = NfcAdapter.getDefaultAdapter(this);
        buildPage();
    }
    private void buildPage() {
        LinearLayout root = column(); root.setBackgroundColor(0xFFF5F7FD);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView back = text("‹", 34, INK, false); back.setGravity(Gravity.CENTER); back.setContentDescription("返回"); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("read".equals(mode) ? ("student".equals(cardType) ? "新增學生證" : "新增門禁卡") : "restore".equals(mode) ? "暫停模擬" : "啟用卡片", 22, INK, true));
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = column(); content.setGravity(Gravity.CENTER); content.setPadding(dp(26), dp(16), dp(26), dp(32));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        PlacementView illustration = new PlacementView(); illustration.setContentDescription("將卡片貼近手機背面 NFC 感應區");
        content.addView(illustration, new LinearLayout.LayoutParams(-1, dp(240)));
        symbol = text("", 36, BLUE, true); symbol.setGravity(Gravity.CENTER); content.addView(symbol);
        title = text("", 25, INK, true); title.setGravity(Gravity.CENTER); content.addView(title, margins(12, 10));
        detail = text("", 15, MUTED, false); detail.setGravity(Gravity.CENTER); detail.setLineSpacing(dp(4), 1); content.addView(detail, margins(0, 28));
        primary = new Button(this); primary.setTextSize(16); primary.setAllCaps(false); primary.setTextColor(0xFFFFFFFF); primary.setMinHeight(dp(56));
        primary.setBackground(new RippleDrawable(ColorStateList.valueOf(0x223C70C8), rounded(BLUE, 18), rounded(0xFFFFFFFF, 18)));
        content.addView(primary, new LinearLayout.LayoutParams(-1, dp(56)));
        secondary = new Button(this); secondary.setText("暫停模擬"); secondary.setTextColor(BLUE); secondary.setMinHeight(dp(48));
        secondary.setBackgroundColor(android.graphics.Color.TRANSPARENT); secondary.setVisibility(View.GONE);
        secondary.setOnClickListener(v -> { getSharedPreferences("nfc-state", MODE_PRIVATE).edit().putBoolean("autoActivate", false).apply(); restore("正在暫停模擬"); });
        content.addView(secondary, margins(8, 0));
        TextView note = text("read".equals(mode) ? "僅添加門禁功能，不導入悠遊卡支付部分" : "僅用於本人有權使用的門禁；付款前請暫停卡片模擬", 12, MUTED, false);
        note.setGravity(Gravity.CENTER); content.addView(note, margins(20, 0));
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if ("restore".equals(mode)) {
            getSharedPreferences("nfc-state", MODE_PRIVATE).edit().putBoolean("autoActivate", false).apply(); restore("正在恢復 NFC");
        } else if ("read".equals(mode)) startRead();
        else if (cardId.equals(VendorCardController.activeCardId(this))) showEnabled();
        else if (!busy) enable();
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(readTimeout);
        stopReader(); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if ("read".equals(mode)) worker.execute(() -> { try { recordRestore(controller.restore()); } catch (Exception error) { recordFailure("RESTORE", error); } });
        super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("cardId", cardId); super.onSaveInstanceState(state); }
    private void status(String heading, String body, String action, Runnable click) {
        title.setText(heading); detail.setText(body); primary.setText(action); primary.setEnabled(click != null);
        primary.setAlpha(click == null ? .55f : 1); primary.setOnClickListener(v -> { if (click != null) click.run(); });
    }
    private boolean nfcReady() {
        if (nfc == null) { status("此手機不支援 NFC", "請使用支援 NFC 的手機。", "返回", this::finish); return false; }
        if (!nfc.isEnabled()) {
            status("請先開啟 NFC", "開啟後回到此頁即可繼續。", "開啟 NFC", () -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS))); return false;
        }
        return true;
    }
    private void startRead() {
        if (busy || !resumed || !nfcReady()) return;
        if (controller.recoveryRequired()) { restore("正在準備讀卡"); return; }
        stopReader(); scanning.set(true); symbol.setText("");
        status("請將卡片貼在 NFC 讀取位置", "將卡片靠近手機背面感應區並保持不動，偵測到後會自動添加。", "正在等待卡片…", null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            nfc.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
            handler.postDelayed(readTimeout, 60_000);
        } catch (RuntimeException error) { stopReader(); status("暫時無法讀取", "請移開卡片後重試。", "重試讀卡", this::startRead); }
    }
    private void stopReader() {
        scanning.set(false); handler.removeCallbacks(readTimeout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (nfc != null) try { nfc.disableReaderMode(this); } catch (RuntimeException ignored) { }
    }
    @Override public void onTagDiscovered(Tag tag) {
        if (!resumed || !scanning.compareAndSet(true, false)) return;
        runOnUiThread(() -> { stopReader(); busy = true; status("已偵測到卡片", "正在安全保存，請稍候…", "正在添加…", null); });
        worker.execute(() -> {
            try {
                NfcA tech = NfcA.get(tag);
                if (tech == null || !Arrays.asList(tag.getTechList()).contains("android.nfc.tech.MifareClassic"))
                    throw new IllegalArgumentException("Unsupported card");
                controller.saveProfile(new AccessCardProfile(tag.getId(), tech.getAtqa(), tech.getSak() & 255));
                CardStore cards = new CardStore(this);
                boolean exists = cards.cards.stream().anyMatch(card -> card.id.equals(cardId));
                if (!exists) cards.cards.add(new CardAppearance(cardId, "student".equals(cardType) ? "學生證" : "門禁卡", cardType, true));
                if (cards.defaultId.isEmpty()) cards.defaultId = cardId;
                cards.save();
                JSONObject report = new JSONObject().put("observedAt", System.currentTimeMillis()).put("technologies", new JSONArray(Arrays.asList(tag.getTechList())))
                        .put("uidByteLength", tag.getId().length).put("uidStoredEncrypted", true).put("uidPlaintextStored", false)
                        .put("cardContentsRead", false).put("cardModified", false).put("automaticEnrollment", true)
                        .put("cardId", cardId).put("cardCount", cards.cards.size()).put("doorUnlockStatus", "NOT_TESTED");
                save("card_probe.json", report);
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("cardSummary", "本人門卡已加密錄入；開門待實測").apply();
                runOnUiThread(() -> {
                    busy = false; if (!resumed) return; symbol.setText("✓");
                    status("卡片已添加", "正在返回我的卡片…", "完成", this::finish);
                    handler.postDelayed(() -> { if (resumed) finish(); }, 900);
                });
            } catch (Exception error) {
                recordFailure("ENROLL", error);
                runOnUiThread(() -> { busy = false; if (!resumed) return;
                    status(error instanceof IllegalArgumentException ? "暫不支援此卡片" : "添加未完成", "請移開卡片後重試，或使用本人學生證的門禁功能。", "重新讀卡", this::startRead); });
            }
        });
    }
    private void enable() {
        if (busy || !nfcReady()) return;
        if (!controller.hasProfile()) { status("請先添加卡片", "此卡片尚未錄入 NFC 資料。", "返回我的卡片", this::finish); return; }
        busy = true; status("正在啟用卡片", "請稍候…", "正在啟用…", null);
        worker.execute(() -> {
            try {
                JSONObject report = controller.activate(); save("vendor_test_enable.json", report);
                getSharedPreferences("nfc-state", MODE_PRIVATE).edit().putBoolean("autoActivate", true).apply();
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("vendorFailure").apply();
                runOnUiThread(() -> { busy = false; if (resumed) showEnabled(); });
            } catch (Exception error) {
                try { recordRestore(controller.restore()); } catch (Exception recovery) { error.addSuppressed(recovery); }
                recordFailure("ACTIVATE_CARD", error);
                runOnUiThread(() -> { busy = false; if (resumed) showFailure(error); });
            }
        });
    }
    private void showEnabled() {
        busy = false; symbol.setText("✓"); secondary.setVisibility(View.VISIBLE);
        status("卡片已啟用", "離開頁面後會保留此卡片。熄屏感應能否開門，請以實際門禁結果為準。", "返回我的卡片", this::finish);
    }
    private void restore(String heading) {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); secondary.setVisibility(View.GONE);
        busy = true; status(heading, "正在確認 NFC 已恢復，請稍候…", "正在恢復…", null);
        worker.execute(() -> {
            try {
                recordRestore(controller.restore());
                runOnUiThread(() -> { busy = false; if (!resumed) return; symbol.setText("✓");
                    if ("read".equals(mode)) { startRead(); return; }
                    status("NFC 已恢復", "卡片模擬已暫停。", "返回我的卡片", this::finish); });
            } catch (Exception error) { recordFailure("RESTORE", error); runOnUiThread(() -> { busy = false; if (resumed) showFailure(error); }); }
        });
    }
    private void showFailure(Exception error) {
        boolean pending = controller.recoveryRequired(); symbol.setText("");
        status(pending ? "NFC 需要恢復" : "暫時無法啟用卡片", pending ? "請保持 NFC 開啟並重試恢復，完成前先不要使用 NFC 付款。"
                : "請確認 NFC 已開啟且手機已完成授權，再重新嘗試。", pending ? "重試恢復" : "重試", pending ? () -> restore("正在恢復 NFC") : this::enable);
    }
    private void recordRestore(JSONObject report) throws Exception { if ("PASS".equals(report.optString("result"))) save("vendor_test_restore.json", report); }
    private void recordFailure(String operation, Exception error) {
        VendorFailure failure = VendorFailure.at(operation, error);
        try { save("vendor_test_failure.json", new JSONObject().put("observedAt", System.currentTimeMillis()).put("operation", operation).put("result", "FAIL")
                .put("stage", failure.stage).put("reasonCode", failure.code).put("errorType", failure.errorType())
                .put("recoveryRequired", controller.recoveryRequired()).put("uidLogged", false).put("doorUnlockStatus", "NOT_TESTED")); }
        catch (Exception ignored) { }
    }
    private void save(String name, JSONObject report) throws Exception {
        try (FileOutputStream output = openFileOutput(name, MODE_PRIVATE)) { output.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(null, Typeface.BOLD); return view; }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private LinearLayout.LayoutParams margins(int top, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); params.bottomMargin = dp(bottom); return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class PlacementView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        PlacementView() { super(NfcFlowActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float unit = Math.min(getWidth() / 300f, getHeight() / 240f);
            canvas.save(); canvas.translate(getWidth() / 2f, getHeight() / 2f); canvas.scale(unit, unit);
            paint.setColor(0xFFE5EEFF); canvas.drawCircle(0, 0, 102, paint);
            paint.setColor(0xFFB8CDEE); rect.set(-54, -101, 54, 96); canvas.drawRoundRect(rect, 18, 18, paint);
            paint.setColor(0xFFFFFFFF); rect.set(-48, -95, 48, 90); canvas.drawRoundRect(rect, 14, 14, paint);
            paint.setColor(0xFFC6D8F5); rect.set(-15, -88, 15, -83); canvas.drawRoundRect(rect, 3, 3, paint);
            paint.setColor(BLUE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
            for (int i = 0; i < 3; i++) { int radius = 10 + i * 9; rect.set(-radius, -25-radius, radius, -25+radius); canvas.drawArc(rect, -55, 110, false, paint); }
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xFF73A4F1); rect.set(-22, 12, 119, 97); canvas.drawRoundRect(rect, 12, 12, paint);
            paint.setColor(0xFFE8F1FF); rect.set(-8, 30, 33, 39); canvas.drawRoundRect(rect, 3, 3, paint); rect.set(-8, 50, 71, 56); canvas.drawRoundRect(rect, 3, 3, paint);
            canvas.restore();
        }
    }
}
