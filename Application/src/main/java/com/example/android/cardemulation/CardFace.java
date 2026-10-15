package com.example.android.cardemulation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.text.TextUtils;
import android.text.TextPaint;
import java.io.File;

final class CardFace extends View {
    private final CardAppearance card;
    private boolean selected;
    private boolean active;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable campus;
    private Bitmap image;
    private boolean draggable, coverOnly;
    private float previousX, previousY;
    CardFace(Context context, CardAppearance card, boolean selected) {
        super(context); this.card = card; this.selected = selected;
        campus = context.getDrawable(R.drawable.campus_card);
        if (!card.image.isEmpty()) {
            String path = new File(context.getFilesDir(), card.image).getPath();
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2;
            try { image = BitmapFactory.decodeFile(path, options); } catch (OutOfMemoryError error) { image = null; }
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFBDDCF0); background.setCornerRadius(dp(18));
        setBackground(background); setClipToOutline(true); setElevation(dp(5));
        setContentDescription(card.name + (selected ? "，預設卡片" : "")
                + (card.linked ? "，已錄入，開門待驗證" : "，外觀卡片，未啟用 NFC"));
    }
    void allowCropDragging() { draggable = true; coverOnly = true; }
    boolean hasImage() { return image != null; }
    void selectDefault(boolean value) { selected = value; invalidate(); }
    void setActive(boolean value) { active = value; invalidate(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width / 1.586f));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width == 0 || height == 0) return;
        if (image != null) {
            float[] crop = CardAppearance.crop(image.getWidth(), image.getHeight(), width, height,
                    card.autoCrop ? 1 : card.zoom, card.autoCrop ? 0 : card.panX, card.autoCrop ? 0 : card.panY);
            canvas.drawBitmap(image, null, new RectF(crop[0], crop[1], crop[2], crop[3]), paint);
        } else {
            campus.setBounds(0, 0, (int) width, (int) height); campus.draw(canvas);
            if ("student".equals(card.type)) {
                paint.setColor(0x70DADCF7); canvas.drawCircle(width * .96f, height * .08f, width * .31f, paint);
                paint.setColor(0x608EDACD); canvas.drawCircle(width * 1.04f, height * .35f, width * .25f, paint);
                paint.setColor(0x80F5CEBF); canvas.drawCircle(width * .02f, height * 1.09f, width * .24f, paint);
            }
        }
        if (coverOnly) {
            paint.setColor(0xAFFFFFFF); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            canvas.drawLine(width / 3, 0, width / 3, height, paint); canvas.drawLine(width * 2 / 3, 0, width * 2 / 3, height, paint);
            canvas.drawLine(0, height / 3, width, height / 3, paint); canvas.drawLine(0, height * 2 / 3, width, height * 2 / 3, paint);
            paint.setStyle(Paint.Style.FILL); return;
        }
        paint.setShader(new LinearGradient(0, 0, width * .8f, height * .5f,
                new int[]{0xDDDBEDFA, 0x00DBEDFA}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint); paint.setShader(null);
        float scale = width / dp(340);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); text.setTextSize(dp(24) * scale); text.setColor(0xFF0F2034);
        CharSequence title = TextUtils.ellipsize(card.name, text, width - dp(48) * scale, TextUtils.TruncateAt.END);
        canvas.drawText(title.toString(), dp(24) * scale, height * .27f, text);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); text.setTextSize(dp(9) * scale);
        String english = "student".equals(card.type) ? "STUDENT ID" : "access".equals(card.type) ? "DORMITORY ACCESS CARD" : "CUSTOM CARD";
        canvas.drawText(english, dp(24) * scale, height * .38f, text);
        paint.setColor(0xFF456486); paint.setStrokeWidth(dp(1.8f) * scale); paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            float radius = dp(5 + i * 4) * scale;
            canvas.drawArc(new RectF(dp(25) * scale - radius, height * .55f - radius,
                    dp(25) * scale + radius, height * .55f + radius), -55, 110, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (selected) {
            paint.setColor(0xFF2C70EE);
            RectF badge = new RectF(width - dp(142) * scale, dp(10) * scale, width - dp(66) * scale, dp(36) * scale);
            canvas.drawRoundRect(badge, dp(13), dp(13), paint);
            text.setColor(Color.WHITE); text.setTextSize(dp(12) * scale);
            canvas.drawText("★  預設", badge.left + dp(10) * scale, badge.top + dp(18) * scale, text);
        }
        if (active) {
            paint.setColor(0xFF2C70EE);
            RectF badge = new RectF(width - dp(86) * scale, height - dp(38) * scale, width - dp(10) * scale, height - dp(10) * scale);
            canvas.drawRoundRect(badge, dp(13), dp(13), paint);
            text.setColor(Color.WHITE); text.setTextSize(dp(12) * scale);
            canvas.drawText("使用中", badge.left + dp(16) * scale, badge.top + dp(19) * scale, text);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!draggable || image == null) return super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            previousX = event.getX(); previousY = event.getY(); getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float[] crop = CardAppearance.crop(image.getWidth(), image.getHeight(), getWidth(), getHeight(), card.zoom, card.panX, card.panY);
            float extraX = crop[2] - crop[0] - getWidth(), extraY = crop[3] - crop[1] - getHeight();
            if (extraX > 0) card.panX = Math.max(-1, Math.min(1, card.panX - (event.getX() - previousX) * 2 / extraX));
            if (extraY > 0) card.panY = Math.max(-1, Math.min(1, card.panY - (event.getY() - previousY) * 2 / extraY));
            previousX = event.getX(); previousY = event.getY(); invalidate(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false); if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick(); return true;
        }
        return true;
    }
    @Override public boolean performClick() { return super.performClick(); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
