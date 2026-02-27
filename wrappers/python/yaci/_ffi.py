"""Low-level ctypes FFI wrapper around libyaci shared library."""

import ctypes
import os
import sys
from ctypes import c_int, c_long, c_char_p, c_void_p, POINTER, byref


class YaciLib:
    """Low-level FFI wrapper around libyaci shared library."""

    # Error codes (mirror of ErrorCodes.java)
    YACI_SUCCESS = 0
    YACI_ERROR_GENERAL = -1
    YACI_ERROR_INVALID_ARGUMENT = -2
    YACI_ERROR_SERIALIZATION = -3
    YACI_ERROR_CONNECTION = -4
    YACI_ERROR_TIMEOUT = -5
    YACI_ERROR_SESSION_NOT_FOUND = -6
    YACI_ERROR_SESSION_ALREADY_STARTED = -7
    YACI_ERROR_SESSION_NOT_STARTED = -8

    def __init__(self, lib_path=None):
        if lib_path is None:
            lib_path = os.environ.get('YACI_LIB_PATH', os.path.join(
                os.path.dirname(__file__), 'lib'))

        # If lib_path points to a file, use it directly; otherwise treat as directory
        if os.path.isfile(lib_path):
            lib_file = lib_path
        elif sys.platform == 'darwin':
            lib_file = os.path.join(lib_path, 'libyaci.dylib')
        elif sys.platform == 'win32':
            lib_file = os.path.join(lib_path, 'libyaci.dll')
        else:
            lib_file = os.path.join(lib_path, 'libyaci.so')

        self._lib = ctypes.CDLL(lib_file)
        self._setup_functions()

        # Create GraalVM isolate
        self._isolate = c_void_p()
        self._thread = c_void_p()
        rc = self._lib.graal_create_isolate(None, byref(self._isolate), byref(self._thread))
        if rc != 0:
            raise RuntimeError(f"Failed to create GraalVM isolate: {rc}")

    def _setup_functions(self):
        lib = self._lib

        # GraalVM isolate lifecycle
        lib.graal_create_isolate.argtypes = [c_void_p, POINTER(c_void_p), POINTER(c_void_p)]
        lib.graal_create_isolate.restype = c_int

        lib.graal_tear_down_isolate.argtypes = [c_void_p]
        lib.graal_tear_down_isolate.restype = c_int

        lib.graal_attach_thread.argtypes = [c_void_p, POINTER(c_void_p)]
        lib.graal_attach_thread.restype = c_int

        lib.graal_detach_thread.argtypes = [c_void_p]
        lib.graal_detach_thread.restype = c_int

        # YaciBridge lifecycle
        lib.yaci_version.argtypes = [c_void_p]
        lib.yaci_version.restype = c_int

        lib.yaci_get_result.argtypes = [c_void_p]
        lib.yaci_get_result.restype = c_void_p

        lib.yaci_get_last_error.argtypes = [c_void_p]
        lib.yaci_get_last_error.restype = c_void_p

        lib.yaci_free_string.argtypes = [c_void_p, c_void_p]
        lib.yaci_free_string.restype = None

        # TipFinder API
        lib.yaci_tip_find.argtypes = [
            c_void_p,   # thread
            c_char_p,   # host
            c_int,      # port
            c_long,     # protocolMagic
            c_long,     # wellKnownSlot
            c_char_p,   # wellKnownHash
            c_long,     # timeoutMs
        ]
        lib.yaci_tip_find.restype = c_int

        # PeerDiscovery API
        lib.yaci_peer_discovery.argtypes = [
            c_void_p,   # thread
            c_char_p,   # host
            c_int,      # port
            c_long,     # protocolMagic
            c_int,      # requestAmount
            c_long,     # timeoutMs
        ]
        lib.yaci_peer_discovery.restype = c_int

        # BlockSync API
        lib.yaci_block_sync_create.argtypes = [
            c_void_p, c_char_p, c_int, c_long, c_long, c_char_p
        ]
        lib.yaci_block_sync_create.restype = c_int

        lib.yaci_block_sync_start.argtypes = [
            c_void_p, c_int, c_long, c_char_p
        ]
        lib.yaci_block_sync_start.restype = c_int

        lib.yaci_block_sync_start_from_tip.argtypes = [c_void_p, c_int]
        lib.yaci_block_sync_start_from_tip.restype = c_int

        lib.yaci_block_sync_poll.argtypes = [c_void_p, c_int, c_long]
        lib.yaci_block_sync_poll.restype = c_int

        lib.yaci_block_sync_stop.argtypes = [c_void_p, c_int]
        lib.yaci_block_sync_stop.restype = c_int

        lib.yaci_block_sync_destroy.argtypes = [c_void_p, c_int]
        lib.yaci_block_sync_destroy.restype = c_int

        # BlockRangeSync API
        lib.yaci_block_range_sync_create.argtypes = [
            c_void_p, c_char_p, c_int, c_long
        ]
        lib.yaci_block_range_sync_create.restype = c_int

        lib.yaci_block_range_sync_start.argtypes = [c_void_p, c_int]
        lib.yaci_block_range_sync_start.restype = c_int

        lib.yaci_block_range_sync_fetch.argtypes = [
            c_void_p, c_int, c_long, c_char_p, c_long, c_char_p
        ]
        lib.yaci_block_range_sync_fetch.restype = c_int

        lib.yaci_block_range_sync_poll.argtypes = [c_void_p, c_int, c_long]
        lib.yaci_block_range_sync_poll.restype = c_int

        lib.yaci_block_range_sync_stop.argtypes = [c_void_p, c_int]
        lib.yaci_block_range_sync_stop.restype = c_int

        lib.yaci_block_range_sync_destroy.argtypes = [c_void_p, c_int]
        lib.yaci_block_range_sync_destroy.restype = c_int

    def attach_thread(self):
        """Attach current OS thread to the GraalVM isolate. Returns IsolateThread handle."""
        thread = c_void_p()
        rc = self._lib.graal_attach_thread(self._isolate, byref(thread))
        if rc != 0:
            raise RuntimeError(f"Failed to attach thread to GraalVM isolate: {rc}")
        return thread

    def detach_thread(self, thread):
        """Detach current OS thread from the GraalVM isolate."""
        self._lib.graal_detach_thread(thread)

    def _get_result(self, thread=None):
        """Get the last result string and free it."""
        t = thread or self._thread
        ptr = self._lib.yaci_get_result(t)
        if not ptr:
            return None
        result = ctypes.string_at(ptr).decode('utf-8')
        self._lib.yaci_free_string(t, ptr)
        return result

    def _get_error(self, thread=None):
        """Get the last error string and free it."""
        t = thread or self._thread
        ptr = self._lib.yaci_get_last_error(t)
        if not ptr:
            return None
        error = ctypes.string_at(ptr).decode('utf-8')
        self._lib.yaci_free_string(t, ptr)
        return error

    def _check(self, rc, thread=None):
        """Check return code and raise if error."""
        if rc != self.YACI_SUCCESS:
            error = self._get_error(thread)
            raise YaciError(rc, error or f"Unknown error (code {rc})")
        return self._get_result(thread)

    def _encode(self, s):
        """Encode string to bytes for C."""
        if s is None:
            return None
        if isinstance(s, str):
            return s.encode('utf-8')
        return s

    def close(self):
        """Tear down the GraalVM isolate."""
        if self._thread:
            self._lib.graal_tear_down_isolate(self._thread)
            self._thread = None

    def __del__(self):
        self.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def version(self):
        """Get bridge version string."""
        rc = self._lib.yaci_version(self._thread)
        return self._check(rc)


class YaciError(Exception):
    """Exception raised for Yaci bridge errors."""

    def __init__(self, code, message):
        self.code = code
        self.message = message
        super().__init__(f"Yaci Error {code}: {message}")
