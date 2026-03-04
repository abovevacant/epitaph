# epitaph

A lightweight decoder for Android tombstones, focused on extracting meaning without adding weight.

## Features

- Decodes Android tombstone protobuf format
- Zero runtime dependencies
- Java 11

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

## Build

```
./gradlew build
```
