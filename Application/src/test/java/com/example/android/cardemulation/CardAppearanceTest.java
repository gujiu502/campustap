package com.example.android.cardemulation;
import org.junit.Test;
import static org.junit.Assert.*;

public class CardAppearanceTest {
    @Test public void validatesNamesAndKeepsDraftSeparate() {
        assertEquals("學生證", CardAppearance.checkedName(" 學生證 "));
        for (String name : new String[]{null, " ", "a".repeat(33)})
            assertThrows(IllegalArgumentException.class, () -> CardAppearance.checkedName(name));
        CardAppearance saved = new CardAppearance("access", "門禁卡", "access", true);
        CardAppearance draft = saved.copy(); draft.name = "新名字"; draft.panX = 1;
        assertEquals("門禁卡", saved.name); assertEquals(0, saved.panX, 0);
    }
    @Test public void centerCropKeepsAspectAndNeverLeavesBlankEdges() {
        float[] bounds = CardAppearance.crop(100, 200, 320, 200, 1, 0, 0);
        assertArrayEquals(new float[]{0,-220,320,420}, bounds, 0.01f);
        for (float pan : new float[]{-5,-1,0,1,5}) {
            bounds = CardAppearance.crop(600, 100, 320, 200, 2, pan, -pan);
            assertTrue(bounds[0] <= 0 && bounds[1] <= 0 && bounds[2] >= 320 && bounds[3] >= 200);
            assertEquals(6, (bounds[2]-bounds[0]) / (bounds[3]-bounds[1]), 0.01f);
        }
    }
    @Test public void rejectsInvalidImageBounds() {
        assertThrows(IllegalArgumentException.class, () -> CardAppearance.crop(0, 10, 20, 30, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CardAppearance.crop(10, 10, 20, 30, Float.NaN, 0, 0));
    }
}
