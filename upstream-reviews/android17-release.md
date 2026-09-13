# Android 17 tombstone schema review

Reviewed 2026-09-13. Implemented locally for the planned **0.2.0** release.

## Finding

Epitaph previously watched only `platform/system/core`'s `main` and the last
modifying commit `981d145117e8992842cdddee555c57e60c7a220a`. At review time,
`main` was at `a3b721a32242006b59cb12bd62c9133632af3a2d` (2025-03-26); its schema
was still byte-identical to that baseline but release branches had advanced.

Comparing the complete `debuggerd/proto/tombstone.proto` files gives:

| Branch                       | Reviewed tree commit                       | Schema relative to old baseline |
|------------------------------|--------------------------------------------|---------------------------------|
| `main`                       | `981d145117e8992842cdddee555c57e60c7a220a` | Identical                       |
| `android16-release`          | `68be0c2c0006a0740d0b1809abe4717308f90d15` | Identical                       |
| `android16-security-release` | `68be0c2c0006a0740d0b1809abe4717308f90d15` | Identical                       |
| `android16-qpr1-release`     | `4deec4059670028cccb7f9f22bb73813ae71c6f3` | Two added fields                |
| `android16-qpr2-release`     | `e9d1fa3705d7fbd0ac1c942bc4c276161bfbfed1` | Same as QPR1                    |
| `android17-release`          | `545d2487e38192a2ce25040897ced877cf6b4f53` | Five added fields               |
| `android17-security-release` | `2de25eeaec5500f0bfbc5d1ae320ca8c702e39f6` | Same as Android 17 release      |

Only additions and corresponding reserved-range adjustments occur; no existing
field numbers, types, enums, or message layouts change. The old decoder skips
the new fields, losing their information without inherently breaking decoding.

| Proto field                 | Type / number | First present among reviewed branches | Java API         |
|-----------------------------|---------------|---------------------------------------|------------------|
| `Tombstone.executable_name` | string / 27   | Android 16 QPR1                       | `executableName` |
| `Tombstone.kernel_release`  | string / 28   | Android 16 QPR1                       | `kernelRelease`  |
| `Tombstone.ppid`            | uint32 / 29   | Android 17                            | `ppid`           |
| `MemoryMapping.vmflags`     | string / 10   | Android 17                            | `vmFlags`        |
| `FD.details`                | string / 5    | Android 17                            | `details`        |

The model and decoder now expose all five fields, with proto3 defaults (`""`
and zero) on older inputs. Existing public constructors remain available and
delegate to new overloads. `Tombstone.Builder` exposes the new tombstone fields.
Like the existing PID API, `ppid` preserves uint32 bits in a Java `int`.

## Validation

- Synthetic tests cover all new fields, duplicate/unknown fields, UTF-8, unsigned parent IDs, absent/empty defaults, invalid wire types, and truncation.
- Existing constructors and old real tombstones keep compatibility coverage.
- A real Android 17 API 37 ARM64 emulator crash exercises **all five fields**, including an unattached BPF descriptor for `FD.details`.
- The full protobuf, debuggerd text, and formatted snapshot are retained. Values were independently checked with protoc and debuggerd. See [`src/test/resources/README.md`](../src/test/resources/README.md) for provenance and reproduction instructions.

Validation passed:

```sh
python3 -m unittest discover -s scripts/tests -v  # 15 offline checker tests
./gradlew clean build javadocJar sourcesJar -Pversion=0.2.0  # JDK 17, 205 Java tests
./scripts/check-proto-schema.sh  # live Git-based check
```

The candidate runtime JAR is 32,353 bytes, has Java 8 class-file version 52,
and `jdeps` reports only `java.base`. A JDK 21 build also passed. The existing
unreleased malformed-input fixes should ship with this release too. The
capture AVD was shut down after validation; the pre-existing emulator was left
running.

## Monitoring policy

`upstream.json` records branch-specific reviewed tree commits and SHA-256 hashes
of complete schema files while keeping the original `main` provenance. The daily
or manually dispatched check:

1. Discovers upstream heads and fails on unreviewed `androidNN-release`,
   `androidNN-qprN-release`, and `androidNN-security-release` branches at or after
   Android 16. Older branches and device-specific/experimental branch families
   are intentionally outside this discovery policy.
2. Checks every configured branch independently, not just the newest version.
   A configured branch disappearing is an error.
3. Fetches schemas at the discovered immutable commits and compares contents,
   not last-modifying commit IDs. Identical cherry-picks and unrelated repository
   commits therefore do not require baseline churn.
4. Requires all imports to be explicitly included in that branch's `schemas`
   map. The current schema is self-contained. Each configured imported schema
   is checked the same way, including its own imports.
5. Treats missing/deleted/renamed files, empty content, malformed responses, and
   Git failures as failures. There is a single Git-based checking path.

The checker uses one shallow, blobless fetch of the configured heads in a
cleaned temporary repository, rather than a full-history clone. Gitiles support
was removed after both its branch-discovery and pinned-schema endpoints
repeatedly returned HTTP 503 during live checks; no alternate transport is needed.

If AOSP introduces a new branch naming convention, we must extend the discovery policy explicitly.