# epitaph
[![Maven Central Version](https://img.shields.io/maven-central/v/com.abovevacant/epitaph)](https://central.sonatype.com/artifact/com.abovevacant/epitaph)

A lightweight decoder for Android tombstones, focused on extracting meaning without adding weight.

## Features

- Decodes Android tombstone protobuf format
- Zero runtime dependencies
- Java 8

## Usage

```java
import com.abovevacant.epitaph.wire.TombstoneDecoder;
import com.abovevacant.epitaph.core.Tombstone;

class Example {
    static void main(String[] args) {
        try (InputStream input = new GZIPInputStream(new FileInputStream("tombstone.pb.gz"))) {
            Tombstone tombstone = TombstoneDecoder.decode(input);

            System.out.println(tombstone.buildFingerprint);
            System.out.println(tombstone.signal.name);       // SIGSEGV
            System.out.println(tombstone.signal.codeName);   // SEGV_MAPERR

            for (Cause cause : tombstone.causes) {
                System.out.println(cause.humanReadable);     // null pointer dereference
            }
        } 
    }
}
```

## Parsed Data

| Field               | Description                                     |
|---------------------|-------------------------------------------------|
| `arch`              | CPU architecture (ARM64, ARM, X86_64, etc.)     |
| `buildFingerprint`  | Android build identifier                        |
| `pid`, `tid`, `uid` | Process/thread/user IDs                         |
| `signal`            | Signal info (number, name, code, fault address) |
| `causes`            | Crash causes with human-readable descriptions   |
| `threads`           | All threads with registers and backtraces       |
| `memoryMappings`    | Loaded libraries and memory regions             |
| `logBuffers`        | Logcat dumps                                    |
| `openFds`           | Open file descriptors                           |

## Proto Schema

This library currently tracks [this version](https://android.googlesource.com/platform/system/core/+/981d145117e8992842cdddee555c57e60c7a220a/debuggerd/proto/tombstone.proto) of the tombstone proto definition.

## Build

```
./gradlew build
```
