package com.abovevacant.epitaph.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.abovevacant.epitaph.core.BacktraceFrame;
import com.abovevacant.epitaph.core.Cause;
import com.abovevacant.epitaph.core.FD;
import com.abovevacant.epitaph.core.LogBuffer;
import com.abovevacant.epitaph.core.LogMessage;
import com.abovevacant.epitaph.core.MemoryMapping;
import com.abovevacant.epitaph.core.Register;
import com.abovevacant.epitaph.core.Tombstone;
import com.abovevacant.epitaph.core.TombstoneThread;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TombstoneDecoderTest {

  static Stream<String> tombstoneTestCases() {
    return Stream.of("tombstone_sigsegv", "tombstone_exception");
  }

  @ParameterizedTest
  @MethodSource("tombstoneTestCases")
  void decodeSnapshot(String testCase) throws IOException {
    String inputFile = testCase + ".pb.gz";
    String expectedFile = testCase + "_expected.txt.gz";

    Tombstone tombstone;
    try (InputStream gzipStream = getClass().getClassLoader().getResourceAsStream(inputFile)) {
      assertNotNull(gzipStream, "Input file not found: " + inputFile);
      try (GZIPInputStream decompressed = new GZIPInputStream(gzipStream)) {
        tombstone = TombstoneDecoder.decode(decompressed);
      }
    }

    String actual = formatTombstone(tombstone);

    InputStream snapshotStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
    if (snapshotStream == null) {
      File snapshotFile = new File("src/test/resources/" + expectedFile);
      try (GZIPOutputStream gzipOut =
              new GZIPOutputStream(Files.newOutputStream(snapshotFile.toPath()));
          Writer writer = new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)) {
        writer.write(actual);
      }
      fail(
          "Snapshot file not found. Created new snapshot at: "
              + snapshotFile.getAbsolutePath()
              + ". Re-run the test to verify.");
      return;
    }

    String expected;
    try (GZIPInputStream gzipIn = new GZIPInputStream(snapshotStream)) {
      expected = loadSnapshot(gzipIn);
    }
    assertEquals(expected.trim(), actual.trim());
  }

  private String loadSnapshot(InputStream stream) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  private static String formatTombstone(Tombstone t) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Tombstone ===\n");
    sb.append("arch: ").append(t.arch).append("\n");
    sb.append("guestArch: ").append(t.guestArch).append("\n");
    sb.append("buildFingerprint: ").append(t.buildFingerprint).append("\n");
    sb.append("revision: ").append(t.revision).append("\n");
    sb.append("timestamp: ").append(t.timestamp).append("\n");
    sb.append("pid: ").append(t.pid).append("\n");
    sb.append("tid: ").append(t.tid).append("\n");
    sb.append("uid: ").append(t.uid).append("\n");
    sb.append("selinuxLabel: ").append(t.selinuxLabel).append("\n");
    sb.append("commandLine: ").append(t.commandLine).append("\n");
    sb.append("processUptime: ").append(t.processUptime).append("\n");
    sb.append("abortMessage: ").append(t.abortMessage).append("\n");
    sb.append("pageSize: ").append(t.pageSize).append("\n");
    sb.append("hasBeen16kbMode: ").append(t.hasBeen16kbMode).append("\n");

    if (t.signal != null) {
      sb.append("\n=== Signal ===\n");
      sb.append("number: ").append(t.signal.number).append("\n");
      sb.append("name: ").append(t.signal.name).append("\n");
      sb.append("code: ").append(t.signal.code).append("\n");
      sb.append("codeName: ").append(t.signal.codeName).append("\n");
      sb.append("hasSender: ").append(t.signal.hasSender).append("\n");
      sb.append("senderUid: ").append(t.signal.senderUid).append("\n");
      sb.append("senderPid: ").append(t.signal.senderPid).append("\n");
      sb.append("hasFaultAddress: ").append(t.signal.hasFaultAddress).append("\n");
      sb.append("faultAddress: 0x").append(Long.toHexString(t.signal.faultAddress)).append("\n");
    }

    sb.append("\n=== Causes ===\n");
    for (int i = 0; i < t.causes.size(); i++) {
      Cause cause = t.causes.get(i);
      sb.append("cause[").append(i).append("]: ").append(cause.humanReadable).append("\n");
    }

    sb.append("\n=== Threads ===\n");
    for (Map.Entry<Integer, TombstoneThread> entry : t.threads.entrySet()) {
      TombstoneThread thread = entry.getValue();
      sb.append("thread[").append(entry.getKey()).append("]: ");
      sb.append("id=").append(thread.id);
      sb.append(", name=").append(thread.name);
      sb.append(", registers=").append(thread.registers.size());
      sb.append(", backtrace=").append(thread.backtrace.size());
      sb.append("\n");

      if (!thread.registers.isEmpty()) {
        sb.append("  registers:\n");
        for (Register reg : thread.registers) {
          sb.append("    ").append(reg.name).append(": 0x").append(Long.toHexString(reg.value));
          sb.append("\n");
        }
      }

      if (!thread.backtrace.isEmpty()) {
        sb.append("  backtrace:\n");
        for (int i = 0; i < thread.backtrace.size(); i++) {
          BacktraceFrame frame = thread.backtrace.get(i);
          sb.append("    #").append(i);
          sb.append(" pc 0x").append(Long.toHexString(frame.pc));
          if (!frame.functionName.isEmpty()) {
            sb.append(" ").append(frame.functionName);
            if (frame.functionOffset != 0) {
              sb.append("+").append(frame.functionOffset);
            }
          }
          if (!frame.fileName.isEmpty()) {
            sb.append(" (").append(frame.fileName).append(")");
          }
          sb.append("\n");
        }
      }
    }

    sb.append("\n=== Memory Mappings ===\n");
    sb.append("count: ").append(t.memoryMappings.size()).append("\n");
    for (int i = 0; i < t.memoryMappings.size(); i++) {
      MemoryMapping m = t.memoryMappings.get(i);
      sb.append("  0x")
          .append(Long.toHexString(m.beginAddress))
          .append("-0x")
          .append(Long.toHexString(m.endAddress));
      sb.append(" ")
          .append(m.read ? "r" : "-")
          .append(m.write ? "w" : "-")
          .append(m.execute ? "x" : "-");
      sb.append(" ").append(m.mappingName);
      sb.append("\n");
    }

    sb.append("\n=== Log Buffers ===\n");
    for (LogBuffer buffer : t.logBuffers) {
      sb.append("buffer: ").append(buffer.name).append(" (").append(buffer.logs.size());
      sb.append(" messages)\n");
      for (int i = 0; i < buffer.logs.size(); i++) {
        LogMessage log = buffer.logs.get(i);
        sb.append("  [").append(log.tag).append("] ").append(log.message).append("\n");
      }
    }

    sb.append("\n=== Open FDs ===\n");
    sb.append("count: ").append(t.openFds.size()).append("\n");
    for (int i = 0; i < t.openFds.size(); i++) {
      FD fd = t.openFds.get(i);
      sb.append("  fd ").append(fd.fd).append(": ").append(fd.path).append("\n");
    }

    return sb.toString();
  }
}
