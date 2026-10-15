package com.example.android.cardemulation;

final class CardAppearance {
    static String profileFile(String id) {
        if ("access".equals(id)) return "access_card.enc";
        if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            throw new IllegalArgumentException("Invalid card identifier");
        return "card-" + id + ".enc";
    }
    String id, name, type, image = "";
    boolean linked, autoCrop = true;
    float zoom = 1, panX, panY;
    CardAppearance(String id, String name, String type, boolean linked) {
        this.id = id; this.name = checkedName(name); this.type = type; this.linked = linked;
    }
    static String checkedName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 32)
            throw new IllegalArgumentException("卡片名稱需為 1–32 個字");
        return name.trim();
    }
    CardAppearance copy() {
        CardAppearance copy = new CardAppearance(id, "未命名", type, linked);
        copy.name = name;
        copy.image = image; copy.autoCrop = autoCrop; copy.zoom = zoom; copy.panX = panX; copy.panY = panY;
        return copy;
    }
    static float[] crop(float imageWidth, float imageHeight, float width, float height, float zoom, float panX, float panY) {
        if (!Float.isFinite(imageWidth + imageHeight + width + height + zoom + panX + panY)
                || imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid image bounds");
        float scale = Math.max(width / imageWidth, height / imageHeight) * Math.max(1, Math.min(3, zoom));
        float drawnWidth = imageWidth * scale, drawnHeight = imageHeight * scale;
        float left = -(drawnWidth - width) * (Math.max(-1, Math.min(1, panX)) + 1) / 2;
        float top = -(drawnHeight - height) * (Math.max(-1, Math.min(1, panY)) + 1) / 2;
        return new float[]{left, top, left + drawnWidth, top + drawnHeight};
    }
}
