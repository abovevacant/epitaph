# Tombstone fixtures

We usually keep old fixtures like `tombstone_sigsegv` and `tombstone_exception` because 
their tests verify defaults for fields absent on older Android versions.

## Android 17: `tombstone_android17_abort`

Captured from a **deliberate native SIGABRT on an Android 17 emulator**,
not synthesized or re-encoded. The protobuf and debuggerd text are complete,
unmodified captures compressed with gzip. They come from a newly created,
account-free test AVD, not a personal device.

- Captured: 2026-09-13, 11:09:00 +0200.
- SDK package: `system-images;android-37.0;google_apis;arm64-v8a`, revision 6.
- AVD: `Epitaph_API_37`, Pixel 7a, ARM64, rootable Google APIs (not Play Store).
- Emulator: 37.1.11.0, build 15917651.
- Android release: 17; API: 37; page size: 4096.
- Fingerprint: `google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
- Kernel: `6.12.58-android16-6-gccafb60de224-ab14828483-4k`.
  The kernel's `android16` label is real; the userspace image is Android 17.
- NDK: 29.0.14206865; target: `aarch64-linux-android24`.
- Producer: `scripts/fixtures/android17-crash.c`.
- Device files: `/data/tombstones/tombstone_01.pb` and `tombstone_01`.
- Crash PID/TID: 4733; parent PID: 4634; worker TID: 4735.

The crash has two threads, registers (including `esr` and `vg`), native
backtraces, memory dumps, 120 mappings, and five open descriptors. It exercises
**all five newly supported fields**:

- executable `/data/local/tmp/epitaph-fixture`;
- kernel release and parent PID;
- a named `[anon:epitaph-fixture]` mapping with `vmflags = "rd wr mr mw me ac"`;
- an open BPF program descriptor with `details = "prog_id: 109"`.

Android 17 only emits FD details for certain descriptors, not every FD. The
producer loads a trivial **unattached, unpinned** socket-filter BPF program to
exercise this field. It is freed when the crashing process closes its FDs; no
filter is attached and no persistent BPF object is created. This requires the
rootable test emulator. An eventfd is also held open. Optional sections such as
log buffers and memory-error causes are naturally absent for this crash; those
remain covered by other and synthetic fixtures.

### Files and independent validation

| File                                        | Purpose                                    |
|---------------------------------------------|--------------------------------------------|
| `tombstone_android17_abort.pb.gz`           | Full native protobuf input                 |
| `tombstone_android17_abort.txt.gz`          | Full debuggerd text, independent reference |
| `tombstone_android17_abort_expected.txt.gz` | Epitaph's formatted regression snapshot    |

SHA-256 of the **decompressed** captures:

```text
e4aeba54b98773466b69d6cd0ecfc37ae880f3f90d95bace1a0f2b3e8c4c67a2  tombstone_android17_abort.pb
1949c405139e3dfca2b7eb99721ac5aaae51082d1ede708767032929a3730efb  tombstone_android17_abort.txt
```

The protobuf was also independently decoded with `protoc --decode=Tombstone`
using `debuggerd/proto/tombstone.proto` at AOSP system/core commit
`545d2487e38192a2ce25040897ced877cf6b4f53`. The dedicated Java fixture assertions
use values checked against both protoc and debuggerd, rather than relying only
on an Epitaph-generated snapshot. Protoc is **not** required to build/test Epitaph
and no protobuf runtime dependency is introduced.

### Reproduce

From the Epitaph repository, using a dedicated emulator (not a personal
device or an AVD with accounts/data):

```sh
SDK="$HOME/Library/Android/sdk"
"$SDK/cmdline-tools/latest/bin/sdkmanager" \
  'system-images;android-37.0;google_apis;arm64-v8a'
printf 'no\n' | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd \
  --name Epitaph_API_37 --device pixel_7a \
  --package 'system-images;android-37.0;google_apis;arm64-v8a'
"$SDK/emulator/emulator" -avd Epitaph_API_37 -port 5556 \
  -no-window -no-audio -no-snapshot -no-boot-anim
```

In a second terminal, wait for `sys.boot_completed=1`, verify release/API, and
use the emulator serial explicitly throughout:

```sh
SDK="$HOME/Library/Android/sdk"
NDK="$SDK/ndk/29.0.14206865"
CC="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang"
"$CC" -g -O0 -fno-omit-frame-pointer -Wl,--build-id=sha1 \
  -Wl,--export-dynamic -Wall -Wextra -Werror \
  scripts/fixtures/android17-crash.c -o /tmp/epitaph-fixture
adb -s emulator-5556 root
adb -s emulator-5556 wait-for-device
adb -s emulator-5556 shell getprop ro.build.version.release
adb -s emulator-5556 shell getprop ro.build.version.sdk
adb -s emulator-5556 push /tmp/epitaph-fixture /data/local/tmp/epitaph-fixture
adb -s emulator-5556 shell chmod 755 /data/local/tmp/epitaph-fixture
adb -s emulator-5556 shell /data/local/tmp/epitaph-fixture
# Expected exit: 134 (SIGABRT). Wait for debuggerd to finish, then identify the
# new tombstone by its executable, PID and abort message; numbering can vary.
adb -s emulator-5556 shell ls -lt /data/tombstones
# Pull the matching tombstone_NN and tombstone_NN.pb, then gzip the full files.
```

Use `linux-x86_64` instead of `darwin-x86_64` for the NDK host on Linux, and an
appropriate emulator image/producer architecture. Addresses, PIDs, build IDs,
BPF IDs, timestamps, and potentially image revisions will differ on recapture;
review and update the assertions and snapshot rather than expecting identical
bytes. Shut down the dedicated AVD with `adb -s emulator-5556 emu kill`.
