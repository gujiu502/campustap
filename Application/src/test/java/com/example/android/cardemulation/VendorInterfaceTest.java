package com.example.android.cardemulation;

import org.junit.Test;
import static org.junit.Assert.*;

public class VendorInterfaceTest {
    public static class Complete {
        public Complete() { throw new AssertionError("Capability checks must not construct adapters"); }
        public void programHceParameters(boolean enabled, byte a, byte b, byte sak, byte[] uid, byte ats, byte[] history) { }
        public void sendPropSetConfig(int a, int b, byte[] value) { }
        public byte[] sendPropGetConfig(int a, int b) { return null; }
        public byte[] getNciConfig(int id) { return null; }
    }
    public static class NoPublicConstructor extends Complete { private NoPublicConstructor() { } }
    public static class WrongReadType {
        public WrongReadType() { }
        public void programHceParameters(boolean enabled, byte a, byte b, byte sak, byte[] uid, byte ats, byte[] history) { }
        public void sendPropSetConfig(int a, int b, byte[] value) { }
        public String sendPropGetConfig(int a, int b) { return "unsupported"; }
        public byte[] getNciConfig(int id) { return null; }
    }
    @Test public void acceptsTheRequiredInterfaceWithoutConstructingIt() {
        assertTrue(VendorInterface.compatible(Complete.class));
    }
    @Test public void rejectsMissingMethodsOrInaccessibleConstructors() {
        assertFalse(VendorInterface.compatible(null));
        assertFalse(VendorInterface.compatible(Object.class));
        assertFalse(VendorInterface.compatible(NoPublicConstructor.class));
    }
    @Test public void rejectsAnIncompatibleReadbackType() {
        assertFalse(VendorInterface.compatible(WrongReadType.class));
    }
}
