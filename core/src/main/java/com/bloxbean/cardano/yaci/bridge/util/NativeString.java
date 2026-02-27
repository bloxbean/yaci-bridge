package com.bloxbean.cardano.yaci.bridge.util;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.nio.charset.StandardCharsets;

public final class NativeString {

    private NativeString() {}

    public static CCharPointer toCString(String str) {
        if (str == null) {
            return WordFactory.nullPointer();
        }
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        CCharPointer ptr = UnmanagedMemory.malloc(bytes.length + 1);
        for (int i = 0; i < bytes.length; i++) {
            ptr.write(i, bytes[i]);
        }
        ptr.write(bytes.length, (byte) 0);
        return ptr;
    }

    public static String toJavaString(CCharPointer ptr) {
        if (ptr.isNull()) {
            return null;
        }
        return CTypeConversion.toJavaString(ptr);
    }
}
