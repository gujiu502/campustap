package com.example.android.cardemulation;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class CardStore {
    private final SharedPreferences preferences;
    final List<CardAppearance> cards = new ArrayList<>();
    String defaultId;
    CardStore(Context context) throws Exception {
        preferences = context.getSharedPreferences("card-appearance", Context.MODE_PRIVATE);
        defaultId = preferences.getString("default", "");
        JSONArray json = new JSONArray(preferences.getString("cards", "[]"));
        for (int i = 0; i < json.length(); i++) cards.add(decode(json.getJSONObject(i)));
    }
    void syncProfile(boolean exists) throws Exception {
        boolean found = false, changed = false;
        for (int i = cards.size() - 1; i >= 0; i--) {
            CardAppearance card = cards.get(i);
            if (!card.linked) continue;
            if (exists) found = true;
            else { cards.remove(i); changed = true; if (card.id.equals(defaultId)) defaultId = ""; }
        }
        if (exists && !found) {
            cards.add(0, new CardAppearance("access", "宿舍門禁卡", "access", true)); changed = true;
        }
        if (changed) save();
    }
    void save() throws Exception {
        JSONArray json = new JSONArray();
        boolean selected = defaultId.isEmpty();
        for (CardAppearance card : cards) {
            if (card.id.equals(defaultId) && card.linked) selected = true;
            json.put(encode(card));
        }
        if (!selected) throw new IllegalStateException("預設只能選擇已錄入的門卡");
        if (!preferences.edit().putString("cards", json.toString()).putString("default", defaultId).commit())
            throw new IllegalStateException("卡片儲存失敗");
    }
    static JSONObject encode(CardAppearance card) throws Exception {
        return new JSONObject().put("id", card.id).put("name", CardAppearance.checkedName(card.name))
                .put("type", card.type).put("linked", card.linked).put("image", card.image)
                .put("autoCrop", card.autoCrop).put("zoom", card.zoom).put("panX", card.panX).put("panY", card.panY);
    }
    static CardAppearance decode(JSONObject json) throws Exception {
        String id = json.getString("id"), type = json.getString("type"), image = json.optString("image", "");
        if (!id.matches("[a-zA-Z0-9-]{1,64}") || !type.matches("access|student|custom")
                || (!image.isEmpty() && !image.matches("cover-[a-f0-9-]{36}\\.png")))
            throw new IllegalArgumentException("Invalid card appearance");
        CardAppearance card = new CardAppearance(id, json.getString("name"), type, json.getBoolean("linked"));
        if (card.linked && !"access".equals(id)) throw new IllegalArgumentException("Invalid linked profile");
        card.image = image; card.autoCrop = json.optBoolean("autoCrop", true);
        card.zoom = (float) json.optDouble("zoom", 1); card.panX = (float) json.optDouble("panX", 0); card.panY = (float) json.optDouble("panY", 0);
        if (!Float.isFinite(card.zoom + card.panX + card.panY)) throw new IllegalArgumentException("Invalid crop");
        return card;
    }
}
