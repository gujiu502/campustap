package com.example.android.cardemulation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Appearance management stays separate from the verified NFC experiment. */
public final class CardsActivity extends Activity {
    private static final int BLUE = 0xFF2C70EE, INK = 0xFF111827, MUTED = 0xFF697386, BG = 0xFFF5F7FD;
    private static final int PICK_IMAGE = 8;
    private LinearLayout root, body;
    private CardStore store;
    private CardAppearance draft;
    private boolean draftDefault;
    private String page = "cards", pendingImage = "";
    private Dialog sheet;
    private boolean recoveryOpened;
    private boolean applyDefaultOnResume = true, activationBusy, visible;
    private String activationMessage = "";
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try {
            store = new CardStore(this);
            if (saved != null) {
                page = saved.getString("page", "cards"); pendingImage = saved.getString("pendingImage", "");
                if (saved.containsKey("draft")) draft = CardStore.decode(new JSONObject(saved.getString("draft")));
                draftDefault = saved.getBoolean("draftDefault", false);
            }
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("卡片資料載入失敗")
                    .setMessage("原資料已保留。請返回後重試；也可以先恢復 NFC。")
                    .setPositiveButton("恢復 NFC", (d,w) -> openFlow("restore", null)).setNegativeButton("返回", (d,w) -> finish()).show();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        visible = true;
        if (store == null) return;
        try { store = new CardStore(this); store.syncProfiles(this); }
        catch (Exception error) { message("卡片同步失敗；原資料已保留"); }
        if (draft != null) showEditor(); else showPage(page);
        if (applyDefaultOnResume) {
            applyDefaultOnResume = false;
            if (!store.defaultId.isEmpty() && getSharedPreferences("nfc-state", MODE_PRIVATE).getBoolean("autoActivate", true)) activateCard(store.defaultId);
        }
        if (!activationBusy && !recoveryOpened && new VendorCardController(this).recoveryRequired()
                && VendorCardController.activeCardId(this).isEmpty()) {
            recoveryOpened = true; openFlow("restore", null);
        }
    }
    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle saved) {
        super.onSaveInstanceState(saved);
        saved.putString("page", page); saved.putString("pendingImage", pendingImage); saved.putBoolean("draftDefault", draftDefault);
        if (draft != null) try { saved.putString("draft", CardStore.encode(draft).toString()); }
        catch (Exception error) { message("編輯狀態未儲存"); }
    }
    @Override protected void onDestroy() {
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
        if (isFinishing()) discardPendingImage();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (draft != null) {
            new AlertDialog.Builder(this).setMessage("放棄尚未儲存的變更？")
                    .setNegativeButton("繼續編輯", null).setPositiveButton("放棄", (d,w) -> {
                        discardPendingImage(); draft = null; showPage("cards");
                    }).show();
        } else if (!"cards".equals(page)) showPage("cards"); else super.onBackPressed();
    }
    private void base(String title, boolean editing) {
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard)); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20), dp(16), dp(20), dp(20));
        if (editing) header.addView(icon("‹", "返回", this::onBackPressed), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = label(title, editing ? 22 : 30, INK, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(56), 1));
        if (!editing && "cards".equals(page)) header.addView(icon("+", "新增卡片", this::showAddSheet), new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        body = column(); body.setPadding(dp(20), 0, dp(20), dp(28)); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!editing) navigation();
    }
    private void showPage(String next) {
        page = next; base("history".equals(page) ? "使用紀錄" : "settings".equals(page) ? "設定" : "我的卡片", false);
        if ("cards".equals(page)) showCards(); else if ("history".equals(page)) showHistory(); else showSettings();
    }
    private void navigation() {
        LinearLayout nav = row(); nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackground(round(Color.WHITE, 20)); nav.setElevation(dp(5));
        String[] titles = {"我的卡片", "使用紀錄", "設定"}, keys = {"cards", "history", "settings"}, symbols = {"▣", "◷", "⚙"};
        for (int i = 0; i < keys.length; i++) {
            final String target = keys[i];
            LinearLayout item = column(); item.setGravity(Gravity.CENTER); item.setMinimumHeight(dp(64));
            int color = page.equals(target) ? BLUE : MUTED;
            item.addView(label(symbols[i], 26, color, false)); item.addView(label(titles[i], 12, color, page.equals(target)));
            item.setBackground(ripple(Color.WHITE, 12)); item.setContentDescription(titles[i]); item.setOnClickListener(v -> showPage(target));
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    }
    private void showCards() {
        if (store.cards.isEmpty()) {
            LinearLayout empty = panel(); empty.setPadding(dp(24), dp(48), dp(24), dp(40));
            TextView glyph = label("▣", 54, BLUE, false); glyph.setGravity(Gravity.CENTER); empty.addView(glyph);
            TextView text = label("尚未新增任何卡片", 20, INK, true); text.setGravity(Gravity.CENTER); empty.addView(text);
            TextView hint = label("先錄入本人學生證，或建立自訂卡面", 14, MUTED, false); hint.setGravity(Gravity.CENTER); empty.addView(hint);
            empty.addView(action("新增卡片", true, this::showAddSheet)); body.addView(empty); return;
        }
        for (CardAppearance card : store.cards) {
            CardFace face = new CardFace(this, card, card.id.equals(store.defaultId));
            face.setActive(card.id.equals(VendorCardController.activeCardId(this)));
            face.setOnClickListener(v -> activateCard(card.id)); face.setFocusable(true);
            FrameLayout frame = new FrameLayout(this); frame.setClipChildren(false); frame.setClipToPadding(false);
            frame.addView(face, new FrameLayout.LayoutParams(-1, -2));
            TextView more = icon("⋮", card.name + "更多選項", () -> { });
            more.setTextSize(28); more.setBackground(ripple(0xCCFFFFFF, 24)); more.setElevation(dp(8));
            more.setOnClickListener(v -> showCardMenu(more, card));
            FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
            menuParams.topMargin = dp(4); menuParams.rightMargin = dp(4); frame.addView(more, menuParams);
            body.addView(frame, spaced(-1, -2, 0, 20));
        }
        LinearLayout state = panel();
        String active = VendorCardController.activeCardId(this);
        boolean configured = new VendorCardController(this).recoveryRequired();
        state.addView(label(activationBusy ? "正在切換卡片…" : !active.isEmpty() ? "卡片已啟用" : "尚未啟用卡片", 16, INK, true));
        state.addView(label(!activationMessage.isEmpty() ? activationMessage : "點卡片即可啟用，右上角三點可編輯。NFC 付款前請暫停模擬；熄屏開門仍需實際驗證。", 13, MUTED, false));
        if (configured) state.addView(action("暫停模擬", false, () -> openFlow("restore", null)));
        else if (!store.defaultId.isEmpty()) state.addView(action("啟用預設卡片", true, () -> activateCard(store.defaultId)));
        body.addView(state);
    }
    private void showCardMenu(View anchor, CardAppearance card) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "編輯卡片");
        if (card.linked) menu.getMenu().add(0, 2, 1, card.id.equals(store.defaultId) ? "目前為預設卡" : "設為預設卡片");
        menu.getMenu().add(0, 3, 2, "刪除卡片");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) edit(card);
            else if (item.getItemId() == 2) {
                String previous = store.defaultId; store.defaultId = card.id;
                try { store.save(); activateCard(card.id); } catch (Exception error) { store.defaultId = previous; message("預設卡片儲存失敗"); }
            } else confirmDelete(card);
            return true;
        }); menu.show();
    }
    private void activateCard(String id) {
        if (activationBusy) return;
        CardAppearance selected = null;
        for (CardAppearance card : store.cards) if (card.id.equals(id)) selected = card;
        if (selected == null || !selected.linked) { message("此卡片尚未錄入 NFC 資料"); return; }
        activationBusy = true; activationMessage = "正在啟用「" + selected.name + "」…";
        if (draft == null) showPage(page);
        VendorCardController.WORKER.execute(() -> {
            try {
                JSONObject report = new VendorCardController(this, id).activate();
                try (FileOutputStream output = openFileOutput("vendor_test_enable.json", MODE_PRIVATE)) { output.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
                getSharedPreferences("nfc-state", MODE_PRIVATE).edit().putBoolean("autoActivate", true).apply();
                activationMessage = "已啟用所選卡片，離開軟體後保留。熄屏開門請以實際門禁結果為準。";
            } catch (Exception error) {
                activationMessage = "暫時無法啟用，請確認 NFC 已開啟且手機已完成授權。";
                try { new VendorCardController(this, id).restore(); } catch (Exception ignored) { }
            }
            runOnUiThread(() -> { activationBusy = false; if (visible && draft == null) showPage(page); });
        });
    }
    private void showAddSheet() {
        sheet = new Dialog(this); sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = column(); content.setPadding(dp(20), dp(12), dp(20), dp(28)); content.setBackground(round(Color.WHITE, 24));
        View handle = new View(this); handle.setBackground(round(0xFFD1D5DE, 3));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(42), dp(5)); handleParams.gravity = Gravity.CENTER_HORIZONTAL; handleParams.bottomMargin = dp(16);
        content.addView(handle, handleParams);
        LinearLayout heading = row(); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("新增卡片", 28, INK, true), new LinearLayout.LayoutParams(0, dp(52), 1));
        heading.addView(icon("×", "關閉新增卡片", () -> sheet.dismiss()), new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(heading);
        addType(content, "▣", "門禁卡", "貼近手機，自動讀取並添加", 0xFFE7F0FE, 0xFF639DE5, () -> openFlow("read", "access"));
        addType(content, "◇", "學生證", "貼近手機，添加學生證門禁功能", 0xFFE4F3EF, 0xFF4FAC97, () -> openFlow("read", "student"));
        addType(content, "▤", "交通卡", "原實體悠遊卡加入尚未支援", 0xFFFFF0E4, 0xFFFF9654,
                () -> new AlertDialog.Builder(this).setTitle("交通卡尚未支援").setMessage("目前無法將原實體悠遊卡加入此手機。此入口不會開啟或更動悠遊付。")
                        .setPositiveButton("知道了", null).show());
        addType(content, "+", "自訂卡片", "自訂名稱與圖片，僅管理外觀", 0xFFEEECFE, 0xFF9D93DF, () -> createAppearance("custom"));
        ScrollView scroll = new ScrollView(this); scroll.addView(content); sheet.setContentView(scroll);
        Window window = sheet.getWindow();
        if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes(); attributes.dimAmount = .36f; window.setAttributes(attributes); window.setGravity(Gravity.BOTTOM); }
        sheet.setCanceledOnTouchOutside(true); sheet.show();
        if (window != null) window.setLayout(-1, -2);
    }
    private void addType(LinearLayout parent, String symbol, String title, String hint, int background, int accent, Runnable click) {
        LinearLayout item = row(); item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(dp(16), dp(12), dp(16), dp(12)); item.setMinimumHeight(dp(88));
        item.setBackground(ripple(background, 16));
        TextView icon = label(symbol, 28, Color.WHITE, true); icon.setGravity(Gravity.CENTER); icon.setBackground(round(accent, 12));
        item.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout words = column(); words.setPadding(dp(16), 0, dp(8), 0); words.addView(label(title, 18, INK, true)); words.addView(label(hint, 12, MUTED, false));
        item.addView(words, new LinearLayout.LayoutParams(0, -2, 1)); item.addView(label("›", 28, MUTED, false));
        item.setOnClickListener(v -> { sheet.dismiss(); click.run(); }); item.setFocusable(true);
        parent.addView(item, spaced(-1, -2, 10, 2));
    }
    private void createAppearance(String type) {
        draft = new CardAppearance(UUID.randomUUID().toString(), "student".equals(type) ? "學生證" : "自訂卡片", type, false);
        draftDefault = false; showEditor();
    }
    private void edit(CardAppearance card) { draft = card.copy(); draftDefault = card.id.equals(store.defaultId); showEditor(); }
    private void showEditor() {
        base("編輯卡片", true);
        CardFace preview = new CardFace(this, draft, draftDefault); body.addView(preview, spaced(-1, -2, 0, 24));
        LinearLayout form = panel(); form.addView(label("卡片名稱", 15, INK, true));
        EditText name = new EditText(this); name.setSingleLine(true); name.setText(draft.name); name.setTextSize(16); name.setTextColor(INK);
        name.setPadding(dp(14), dp(12), dp(14), dp(12)); name.setBackground(round(0xFFF1F3F8, 12)); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        name.setContentDescription("卡片名稱"); form.addView(name, spaced(-1, dp(52), 8, 14));
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { draft.name = s.toString(); preview.invalidate(); }
            public void afterTextChanged(Editable editable) { }
        });
        form.addView(label("卡片圖片", 15, INK, true));
        LinearLayout picture = row(); picture.setGravity(Gravity.CENTER_VERTICAL);
        CardFace thumbnail = new CardFace(this, draft, false); picture.addView(thumbnail, new LinearLayout.LayoutParams(dp(128), -2));
        Button replace = action("▧  更換圖片", false, this::pickImage); LinearLayout.LayoutParams replaceParams = new LinearLayout.LayoutParams(0, dp(64), 1); replaceParams.leftMargin = dp(16); picture.addView(replace, replaceParams);
        form.addView(picture, spaced(-1, -2, 8, 12));
        form.addView(toggle("自動裁切圖片", "中心裁切為實體卡比例，保持圖片比例", draft.autoCrop, true, enabled -> {
            draft.autoCrop = enabled; preview.invalidate(); thumbnail.invalidate();
        }));
        form.addView(action("調整裁切位置", false, () -> {
            if (!preview.hasImage()) { message("請先選擇圖片"); return; }
            draft.autoCrop = false; showCrop();
        })); body.addView(form, spaced(-1, -2, 0, 14));
        LinearLayout defaults = panel();
        defaults.addView(toggle("設為預設 NFC 卡片", draft.linked ? "軟體啟動時自動啟用此卡，不改變付款錢包"
                : "此卡僅管理外觀，尚未具備 NFC 感應功能", draftDefault, draft.linked, value -> { draftDefault = value; preview.selectDefault(value); }));
        body.addView(defaults, spaced(-1, -2, 0, 14));
        if (store.cards.stream().anyMatch(card -> card.id.equals(draft.id))) body.addView(action("刪除卡片", false, this::confirmDelete));
        Button save = action("儲存", true, this::saveDraft); LinearLayout.LayoutParams params = spaced(-1, dp(56), 8, 12); params.leftMargin = dp(20); params.rightMargin = dp(20);
        root.addView(save, params);
    }
    private LinearLayout toggle(String title, String hint, boolean checked, boolean enabled, java.util.function.Consumer<Boolean> change) {
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(0, dp(10), 0, dp(10));
        LinearLayout text = column(); text.addView(label(title, 15, INK, true)); text.addView(label(hint, 12, MUTED, false)); line.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this); control.setShowText(false); control.setChecked(checked); control.setEnabled(enabled); control.setContentDescription(title);
        control.setMinimumWidth(dp(52)); control.setMinimumHeight(dp(48)); control.setOnCheckedChangeListener((button, value) -> change.accept(value)); line.addView(control); return line;
    }
    private void showCrop() {
        CardAppearance working = draft.copy(); working.autoCrop = false;
        LinearLayout box = column(); box.setPadding(dp(20), dp(12), dp(20), dp(12));
        box.addView(label("拖曳圖片調整位置，滑桿縮放", 14, MUTED, false));
        CardFace crop = new CardFace(this, working, false); crop.allowCropDragging(); box.addView(crop, spaced(-1, -2, 12, 12));
        SeekBar zoom = new SeekBar(this); zoom.setMax(200); zoom.setProgress(Math.round((working.zoom - 1) * 100)); zoom.setContentDescription("裁切縮放");
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view, int progress, boolean fromUser) { working.zoom = 1 + progress / 100f; crop.invalidate(); }
            public void onStartTrackingTouch(SeekBar view) { } public void onStopTrackingTouch(SeekBar view) { }
        }); box.addView(zoom);
        new AlertDialog.Builder(this).setTitle("調整卡片圖片").setView(box).setNegativeButton("取消", (d,w) -> showEditor())
                .setNeutralButton("重設", (d,w) -> { draft.zoom = 1; draft.panX = draft.panY = 0; showEditor(); })
                .setPositiveButton("確認", (d,w) -> { draft.zoom = working.zoom; draft.panX = working.panX; draft.panY = working.panY; showEditor(); }).show();
    }
    private void pickImage() {
        try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE), PICK_IMAGE); }
        catch (android.content.ActivityNotFoundException error) { message("找不到系統圖片選擇器"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || draft == null) return;
        Uri uri = data.getData(); if (uri == null) return;
        String imported = "cover-" + UUID.randomUUID() + ".png";
        AtomicFile file = new AtomicFile(new File(getFilesDir(), imported));
        try {
            Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (decoder, info, source) -> {
                int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                float scale = Math.min(1, 1600f / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            FileOutputStream out = file.startWrite();
            try { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("Image encode failed"); file.finishWrite(out); }
            catch (Exception error) { file.failWrite(out); throw error; }
            bitmap.recycle(); discardPendingImage(); pendingImage = imported;
            draft.image = imported; draft.zoom = 1; draft.panX = draft.panY = 0; showEditor();
        } catch (Exception | OutOfMemoryError error) { file.delete(); message("圖片載入失敗，請重新選擇較小的圖片"); }
    }
    private void saveDraft() {
        try {
            draft.name = CardAppearance.checkedName(draft.name);
            java.util.List<CardAppearance> previous = new java.util.ArrayList<>(store.cards);
            String previousDefault = store.defaultId, previousImage = "";
            int index = -1;
            for (int i = 0; i < store.cards.size(); i++) if (store.cards.get(i).id.equals(draft.id)) { index = i; previousImage = store.cards.get(i).image; break; }
            if (index < 0) store.cards.add(draft.copy()); else store.cards.set(index, draft.copy());
            if (draftDefault && draft.linked) store.defaultId = draft.id; else if (draft.id.equals(store.defaultId)) store.defaultId = "";
            try { store.save(); } catch (Exception error) { store.cards.clear(); store.cards.addAll(previous); store.defaultId = previousDefault; throw error; }
            pendingImage = ""; if (!previousImage.equals(draft.image)) removeUnusedCover(previousImage);
            draft = null; showPage("cards"); message("卡片已儲存");
            if (!previousDefault.equals(store.defaultId) && !store.defaultId.isEmpty()) activateCard(store.defaultId);
        } catch (Exception error) { message(error instanceof IllegalArgumentException ? "卡片名稱需為 1–32 個字" : "儲存失敗；原卡片資料已保留"); }
    }
    private void confirmDelete() {
        confirmDelete(draft);
    }
    private void confirmDelete(CardAppearance target) {
        new AlertDialog.Builder(this).setTitle("刪除這張卡片？")
                .setMessage(target.linked ? "會刪除此卡片的加密資料；若它正在使用，會先暫停模擬。不修改實體卡。" : "只會刪除此 App 的卡片外觀與圖片。")
                .setNegativeButton("取消", null).setPositiveButton("刪除", (d,w) -> {
                    try {
                        if (target.linked) new VendorCardController(this, target.id).forgetProfile();
                        java.util.List<CardAppearance> previous = new java.util.ArrayList<>(store.cards); String selected = store.defaultId;
                        store.cards.removeIf(card -> card.id.equals(target.id)); if (target.id.equals(store.defaultId)) store.defaultId = "";
                        try { store.save(); } catch (Exception error) { store.cards.clear(); store.cards.addAll(previous); store.defaultId = selected; throw error; }
                        removeUnusedCover(target.image); discardPendingImage(); draft = null; showPage("cards");
                    } catch (Exception error) { message("刪除未完成；請先到設定恢復 NFC"); }
                }).show();
    }
    private void removeUnusedCover(String name) {
        if (!name.matches("cover-[a-f0-9-]{36}\\.png")) return;
        for (CardAppearance card : store.cards) if (name.equals(card.image)) return;
        new AtomicFile(new File(getFilesDir(), name)).delete();
    }
    private void discardPendingImage() {
        if (!pendingImage.isEmpty() && store != null) removeUnusedCover(pendingImage);
        pendingImage = "";
    }
    private void showHistory() {
        body.addView(label("卡片切換與 NFC 恢復紀錄", 14, MUTED, false), spaced(-1, -2, 0, 18));
        boolean found = false;
        String[] reports = {"vendor_test_failure.json", "vendor_test_restore.json", "vendor_test_enable.json"};
        java.util.Arrays.sort(reports, (first, second) -> Long.compare(
                getFileStreamPath(second).lastModified(), getFileStreamPath(first).lastModified()));
        for (String name : reports) {
            File file = getFileStreamPath(name); if (!file.exists()) continue;
            try {
                if (file.length() > 16384) throw new IllegalStateException("Report too large");
                JSONObject report = new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                LinearLayout item = panel();
                String operation = report.optString("operation"), title = "RESTORE".equals(operation) ? "恢復 NFC" : "ENROLL".equals(operation) ? "添加卡片" : "啟用卡片";
                boolean pass = "PASS".equals(report.optString("result", report.optString("controllerReadback")));
                item.addView(label(title + " · " + (pass ? "已完成" : "未完成"), 17, INK, true));
                long timestamp = report.optLong("observedAt", 0);
                if (timestamp > 0) item.addView(label(android.text.format.DateFormat.format("MM/dd HH:mm", timestamp).toString(), 13, MUTED, false));
                item.addView(label("開門結果：尚未驗證", 13, MUTED, false)); body.addView(item, spaced(-1, -2, 0, 12)); found = true;
            } catch (Exception error) { body.addView(label("一筆診斷未能載入；檔案已保留", 13, MUTED, false)); }
        }
        if (!found) { body.addView(label("尚無使用紀錄", 22, INK, true)); body.addView(label("卡片啟用與暫停結果會顯示於此。", 14, MUTED, false)); }
    }
    private void showSettings() {
        LinearLayout status = panel(); status.addView(label("NFC 與門禁", 20, INK, true));
        status.addView(label("是否能開門，請以現場門禁結果為準。目前不支援添加原實體悠遊卡的支付功能。", 14, MUTED, false));
        status.addView(action("暫停模擬並恢復 NFC", true, () -> openFlow("restore", null))); body.addView(status, spaced(-1, -2, 0, 16));
        LinearLayout privacy = panel(); privacy.addView(label("卡片資料與預設", 18, INK, true));
        privacy.addView(label("每張門卡分別加密保存在本機，卡面圖片不會上傳。啟動時自動使用預設卡片；不改變手機付款錢包或悠遊付。", 14, MUTED, false));
        TextView version = label("CampusTap · " + BuildConfig.VERSION_NAME, 13, MUTED, false);
        if (BuildConfig.DEBUG) version.setOnLongClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); return true; });
        privacy.addView(version); body.addView(privacy);
    }
    private void openFlow(String mode, String type) {
        if ("read".equals(mode)) applyDefaultOnResume = true;
        recoveryOpened = true; // The flow owns its asynchronous restore when returning here.
        startActivity(new Intent(this, NfcFlowActivity.class).putExtra("mode", mode).putExtra("cardType", type));
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private LinearLayout panel() { LinearLayout layout = column(); layout.setBackground(round(Color.WHITE, 18)); layout.setPadding(dp(18), dp(12), dp(18), dp(14)); return layout; }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); view.setPadding(0, dp(5), 0, dp(5)); return view;
    }
    private Button action(String text, boolean primary, Runnable click) {
        Button button = new Button(this); button.setText(text); button.setTextSize(15); button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); button.setTextColor(primary ? Color.WHITE : BLUE);
        button.setBackground(ripple(primary ? BLUE : 0xFFEAF1FF, 16)); button.setMinimumHeight(dp(52));
        button.setPadding(dp(12), dp(10), dp(12), dp(10)); button.setOnClickListener(v -> click.run()); return button;
    }
    private TextView icon(String symbol, String description, Runnable click) {
        TextView view = label(symbol, 32, "+".equals(symbol) ? Color.WHITE : INK, false); view.setGravity(Gravity.CENTER);
        view.setBackground(ripple("+".equals(symbol) ? BLUE : Color.TRANSPARENT, 26)); view.setContentDescription(description); view.setFocusable(true); view.setOnClickListener(v -> click.run()); return view;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(0x223C70C8), round(color, radius), round(Color.WHITE, radius)); }
    private LinearLayout.LayoutParams spaced(int width, int height, int top, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height); params.topMargin = dp(top); params.bottomMargin = dp(bottom); return params; }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
