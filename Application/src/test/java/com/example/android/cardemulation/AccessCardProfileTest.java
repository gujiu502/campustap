package com.example.android.cardemulation;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessCardProfileTest {
    @Test public void acceptsObservedShapeAndCopiesInput() {
        byte[] uid = {1, 2, 3, 4}, atqa = {4, 0};
        AccessCardProfile profile = new AccessCardProfile(uid, atqa, 8);
        uid[0] = 9; atqa[0] = 0;
        assertArrayEquals(new byte[]{1,2,3,4}, profile.uid);
        assertArrayEquals(new byte[]{4,0}, profile.atqa);
        assertEquals(8, profile.sak);
    }
    @Test public void rejectsOtherOrMalformedProfiles() {
        for (byte[] uid : new byte[][]{null, {}, {1}, {1,2,3}, {1,2,3,4,5,6,7}})
            assertThrows(IllegalArgumentException.class, () -> new AccessCardProfile(uid, new byte[]{4,0}, 8));
        assertThrows(IllegalArgumentException.class, () -> new AccessCardProfile(new byte[4], null, 8));
        assertThrows(IllegalArgumentException.class, () -> new AccessCardProfile(new byte[4], new byte[]{4}, 8));
        assertThrows(IllegalArgumentException.class, () -> new AccessCardProfile(new byte[4], new byte[]{4,1}, 8));
        assertThrows(IllegalArgumentException.class, () -> new AccessCardProfile(new byte[4], new byte[]{4,0}, 32));
    }
}
