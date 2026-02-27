package com.bloxbean.cardano.yaci.bridge;

import com.bloxbean.cardano.yaci.bridge.util.ErrorState;
import com.bloxbean.cardano.yaci.bridge.util.NativeString;
import com.bloxbean.cardano.yaci.bridge.util.ResultState;
import com.bloxbean.cardano.yaci.core.config.YaciConfig;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

public final class YaciBridge {

    private static final String VERSION = "0.1.0";

    static {
        // Enable block CBOR return by default so consumers can parse full blocks
        YaciConfig.INSTANCE.setReturnBlockCbor(true);
        YaciConfig.INSTANCE.setReturnTxBodyCbor(true);
    }

    private YaciBridge() {}

    @CEntryPoint(name = "yaci_version")
    public static int version(IsolateThread thread) {
        ResultState.set(VERSION);
        return ErrorCodes.YACI_SUCCESS;
    }

    @CEntryPoint(name = "yaci_get_result")
    public static CCharPointer getResult(IsolateThread thread) {
        String result = ResultState.get();
        if (result == null) {
            return NativeString.toCString("");
        }
        return NativeString.toCString(result);
    }

    @CEntryPoint(name = "yaci_get_last_error")
    public static CCharPointer getLastError(IsolateThread thread) {
        String error = ErrorState.get();
        if (error == null) {
            return NativeString.toCString("");
        }
        return NativeString.toCString(error);
    }

    @CEntryPoint(name = "yaci_free_string")
    public static void freeString(IsolateThread thread, CCharPointer ptr) {
        if (ptr.isNonNull()) {
            UnmanagedMemory.free(ptr);
        }
    }
}
