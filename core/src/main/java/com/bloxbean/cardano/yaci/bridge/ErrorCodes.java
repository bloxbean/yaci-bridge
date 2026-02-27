package com.bloxbean.cardano.yaci.bridge;

public final class ErrorCodes {
    public static final int YACI_SUCCESS = 0;
    public static final int YACI_ERROR_GENERAL = -1;
    public static final int YACI_ERROR_INVALID_ARGUMENT = -2;
    public static final int YACI_ERROR_SERIALIZATION = -3;
    public static final int YACI_ERROR_CONNECTION = -4;
    public static final int YACI_ERROR_TIMEOUT = -5;
    public static final int YACI_ERROR_SESSION_NOT_FOUND = -6;
    public static final int YACI_ERROR_SESSION_ALREADY_STARTED = -7;
    public static final int YACI_ERROR_SESSION_NOT_STARTED = -8;

    private ErrorCodes() {}
}
