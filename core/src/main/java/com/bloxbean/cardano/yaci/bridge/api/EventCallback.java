package com.bloxbean.cardano.yaci.bridge.api;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;

/**
 * C function pointer interface for push-based event delivery.
 * Called from Java dispatcher thread when events are available.
 */
public interface EventCallback extends CFunctionPointer {
    @InvokeCFunctionPointer
    void invoke(int sessionId, CCharPointer eventJson);
}
