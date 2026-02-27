# Yaci Bridge — convenience targets
#
# Usage:
#   make build            Build native lib from source (needs GraalVM)
#   make test-python      Run Python wrapper tests
#   make test-all         Build + run all wrapper tests
#   make clean            Clean build artifacts

LIB_DIR := core/build/native/nativeCompile

# Detect platform
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
  LIB_FILE := libyaci.dylib
else
  LIB_FILE := libyaci.so
endif

.PHONY: build test-python test-all clean

build:
	./gradlew :core:nativeCompile

test-python:
	PYTHONPATH=wrappers/python \
	YACI_LIB_PATH=$(LIB_DIR) \
	DYLD_LIBRARY_PATH=$(LIB_DIR) \
	LD_LIBRARY_PATH=$(LIB_DIR) \
	  python3 -m pytest wrappers/python/tests/ -v

test-all: build test-python

clean:
	./gradlew clean
	rm -rf wrappers/python/yaci/lib/
