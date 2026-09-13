package com.abovevacant.epitaph.wire;

import static org.junit.jupiter.api.Assertions.*;

import com.abovevacant.epitaph.core.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TombstoneDecoderAndroid17Test {
  private static byte[] varint(long value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while ((value & ~0x7fL) != 0) {
      out.write((int) (value & 0x7f) | 0x80);
      value >>>= 7;
    }
    out.write((int) value);
    return out.toByteArray();
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) out.write(part, 0, part.length);
    return out.toByteArray();
  }

  private static byte[] number(int field, long value) {
    return concat(varint((long) field << 3), varint(value));
  }

  private static byte[] message(int field, byte[] value) {
    return concat(varint(((long) field << 3) | 2), varint(value.length), value);
  }

  private static byte[] text(int field, String value) {
    return message(field, value.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void allNewFieldsIncludingDuplicatesUnknownFieldsAndUnsignedPpid() throws IOException {
    Tombstone t =
        TombstoneDecoder.decode(
            concat(
                text(27, "old"),
                text(28, "old"),
                number(29, 123),
                message(
                    17, concat(text(10, "old"), number(1000, 7), text(10, "rd wr mr mw me ac"))),
                message(
                    19,
                    concat(text(5, "old"), text(1000, "OEM extension"), text(5, "prog_id: 17"))),
                text(27, "/data/local/tmp/程序"),
                text(28, "6.12.58-android16"),
                number(29, 0xffffffffL),
                number(5, 42),
                text(1000, "future field")));
    assertEquals("/data/local/tmp/程序", t.executableName);
    assertEquals("6.12.58-android16", t.kernelRelease);
    assertEquals(0xffffffffL, Integer.toUnsignedLong(t.ppid));
    assertEquals(42, t.pid);
    assertEquals("rd wr mr mw me ac", t.memoryMappings.get(0).vmFlags);
    assertEquals("prog_id: 17", t.openFds.get(0).details);
  }

  @Test
  void absentAndExplicitlyEmptyFieldsHaveProto3Defaults() throws IOException {
    for (byte[] data :
        new byte[][] {
          concat(message(17, new byte[0]), message(19, new byte[0])),
          concat(
              text(27, ""),
              text(28, ""),
              number(29, 0),
              message(17, text(10, "")),
              message(19, text(5, "")))
        }) {
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals("", t.executableName);
      assertEquals("", t.kernelRelease);
      assertEquals(0, t.ppid);
      assertEquals("", t.memoryMappings.get(0).vmFlags);
      assertEquals("", t.openFds.get(0).details);
    }
  }

  @Test
  void android16QprFieldsDoNotRequireAndroid17Fields() throws IOException {
    Tombstone t = TombstoneDecoder.decode(concat(text(27, "/system/bin/app"), text(28, "6.6")));
    assertEquals("/system/bin/app", t.executableName);
    assertEquals("6.6", t.kernelRelease);
    assertEquals(0, t.ppid);
  }

  static Stream<byte[]> wrongWireTypes() {
    return Stream.of(
        number(27, 1),
        number(28, 1),
        text(29, "wrong"),
        message(17, number(10, 1)),
        message(19, number(5, 1)));
  }

  @ParameterizedTest
  @MethodSource("wrongWireTypes")
  void rejectsWrongWireTypes(byte[] data) {
    assertThrows(IOException.class, () -> TombstoneDecoder.decode(data));
  }

  static Stream<byte[]> newFields() {
    return Stream.of(
        text(27, "path"),
        text(28, "kernel"),
        number(29, 128),
        message(17, text(10, "rd wr")),
        message(19, text(5, "prog_id: 1")));
  }

  @ParameterizedTest
  @MethodSource("newFields")
  void rejectsTruncatedNewFields(byte[] data) {
    assertThrows(
        IOException.class, () -> TombstoneDecoder.decode(Arrays.copyOf(data, data.length - 1)));
  }

  @Test
  void legacyConstructorsRemainAvailableWithNewFieldDefaults() {
    Tombstone t =
        new Tombstone(
            Architecture.ARM64,
            Architecture.NONE,
            "fp",
            "r",
            "ts",
            10,
            11,
            12,
            "label",
            Collections.singletonList("cmd"),
            13,
            null,
            "abort",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            4096,
            false,
            null);
    assertEquals(10, t.pid);
    assertEquals(11, t.tid);
    assertEquals(12, t.uid);
    assertEquals(13, t.processUptime);
    assertEquals("cmd", t.commandLine.get(0));
    assertEquals(4096, t.pageSize);
    assertEquals(0, t.ppid);
    assertEquals("", t.executableName);
    assertEquals("", t.kernelRelease);
    MemoryMapping m = new MemoryMapping(1, 2, 3, true, false, true, "lib.so", "id", 4);
    assertEquals("", m.vmFlags);
    assertEquals(4, m.loadBias);
    FD fd = new FD(7, "path", "owner", 8);
    assertEquals("", fd.details);
    assertEquals(8, fd.tag);
  }

  private Tombstone fixture(String name) throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/" + name + ".pb.gz")) {
      assertNotNull(input);
      try (GZIPInputStream gzip = new GZIPInputStream(input)) {
        return TombstoneDecoder.decode(gzip);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"tombstone_sigsegv", "tombstone_exception"})
  void olderRealTombstonesHaveNewFieldDefaults(String name) throws IOException {
    Tombstone t = fixture(name);
    assertEquals(0, t.ppid);
    assertEquals("", t.executableName);
    assertEquals("", t.kernelRelease);
    for (MemoryMapping mapping : t.memoryMappings) assertEquals("", mapping.vmFlags);
    for (FD fd : t.openFds) assertEquals("", fd.details);
  }

  @Test
  void capturedAndroid17CrashMatchesDebuggerdAndProtoc() throws IOException {
    // Values independently checked against debuggerd's text output and protoc,
    // not generated from Epitaph. See src/test/resources/README.md for provenance.
    Tombstone t = fixture("tombstone_android17_abort");
    assertEquals(Architecture.ARM64, t.arch);
    assertEquals(
        "google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys",
        t.buildFingerprint);
    assertEquals(4733, t.pid);
    assertEquals(4733, t.tid);
    assertEquals(4634, t.ppid);
    assertEquals("/data/local/tmp/epitaph-fixture", t.executableName);
    assertEquals("6.12.58-android16-6-gccafb60de224-ab14828483-4k", t.kernelRelease);
    assertEquals("epitaph Android 17 fixture: deliberate SIGABRT", t.abortMessage);
    assertEquals(6, t.signal.number);
    assertEquals(-1, t.signal.code);
    assertEquals(4096, t.pageSize);
    assertEquals(2, t.threads.size());
    assertEquals("epitaph-worker", t.threads.get(4735).name);
    assertEquals(4, t.threads.get(t.tid).backtrace.size());
    assertEquals("epitaph_fixture_crash", t.threads.get(t.tid).backtrace.get(1).functionName);
    assertTrue(t.threads.get(t.tid).registers.stream().anyMatch(r -> r.name.equals("vg")));
    assertEquals(120, t.memoryMappings.size());
    MemoryMapping named =
        t.memoryMappings.stream()
            .filter(m -> m.mappingName.equals("[anon:epitaph-fixture]"))
            .findFirst()
            .orElseThrow(AssertionError::new);
    assertEquals("rd wr mr mw me ac", named.vmFlags);
    assertEquals(4096, named.endAddress - named.beginAddress);
    assertEquals(5, t.openFds.size());
    FD bpf =
        t.openFds.stream()
            .filter(fd -> fd.path.equals("anon_inode:bpf-prog"))
            .findFirst()
            .orElseThrow(AssertionError::new);
    assertEquals(3, bpf.fd);
    assertEquals("prog_id: 109", bpf.details);
  }
}
