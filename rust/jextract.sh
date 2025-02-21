#!/bin/sh
jextract \
 --include-dir bindings/ \
 --output ../java/src/main/java \
 --target-package golf.tweede.gen \
 --library :../rust/target/release/libjava_interop.so \
 bindings/java_interop.h
