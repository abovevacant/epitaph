# epitaph
[![Maven Central Version](https://img.shields.io/maven-central/v/com.abovevacant/epitaph)](https://central.sonatype.com/artifact/com.abovevacant/epitaph)
[![codecov](https://codecov.io/gh/abovevacant/epitaph/graph/badge.svg)](https://codecov.io/gh/abovevacant/epitaph)

A lightweight ([runtime JAR: 32.4 KB](https://repo.maven.apache.org/maven2/com/abovevacant/epitaph/0.2.0/)) decoder for Android tombstones, focused on extracting meaning without adding weight. <!-- release:jar-link -->

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

The current source implements the [Android 17 schema](https://android.googlesource.com/platform/system/core/+/545d2487e38192a2ce25040897ced877cf6b4f53/debuggerd/proto/tombstone.proto), including `Tombstone.executableName`, `kernelRelease`, `ppid`, `MemoryMapping.vmFlags`, and `FD.details`. See the [changelog](CHANGELOG.md) for availability in published releases. Older tombstones use empty-string/zero defaults for these fields; existing constructors remain available.

[Branch-specific baselines](upstream.json) independently monitor `main`, Android 16 release/QPR/security branches, and Android 17 release/security branches. The daily/manual check also fails when a new major, QPR, or security branch appears from Android 16 onward. It compares schema contents rather than commit IDs and requires review before advancing baselines. See the [upstream review](upstream-reviews/android17-release.md).

```sh
./scripts/check-proto-schema.sh                # shallow Git fetch
python3 -m unittest discover -s scripts/tests -v  # offline checker tests
```

The checker needs Python 3.10+ (standard library only) and Git. These are development tools, not library dependencies.

Tests include a [complete real Android 17 emulator crash](src/test/resources/README.md), with the protobuf, debuggerd text, and reproduction source.

## Build

```
./gradlew build
```

## Release preflight

```sh
./scripts/release.py --check <version>
# Check only credentials, including when a local release tag already exists:
./gradlew --no-configuration-cache checkCentralPortalCredentials
```

Both `--check` and an actual `release.py <version>` invocation independently verify
Central Portal credentials and namespace access before fetching refs or changing
release files, commits, or tags. A previous successful check is never reused.
The probe uses the publishing task's effective credentials and a read-only
[list-deployments request](https://central.sonatype.com/api-doc); it does not
upload, publish, or delete anything. Credentials are passed from Gradle over
stdin, not logged or written to a temporary file.

Configure the Portal **user-token** username/password pair using
`centralPortalUsername` / `centralPortalPassword` Gradle properties or
`CENTRAL_PORTAL_USERNAME` / `CENTRAL_PORTAL_PASSWORD` environment variables.
The Gradle properties take precedence. Authentication failures, namespace access
failures, and network/API errors stop the release. This checks access, not bundle
validation or a guarantee that a later upload cannot fail.
