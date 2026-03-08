package com.abovevacant.epitaph.wire;

import static org.junit.jupiter.api.Assertions.*;

import com.abovevacant.epitaph.core.*;
import com.abovevacant.epitaph.io.WireReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests using synthetic (hand-crafted) protobuf input to cover fields not present in real tombstone
 * snapshots. Each test decodes an isolated sub-message directly.
 */
class TombstoneDecoderSyntheticTest {

  // --- Protobuf encoding helpers ---

  private static byte[] varint(long value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while ((value & ~0x7FL) != 0) {
      out.write((int) ((value & 0x7F) | 0x80));
      value >>>= 7;
    }
    out.write((int) value);
    return out.toByteArray();
  }

  private static byte[] tag(int fieldNumber, int wireType) {
    return varint(((long) fieldNumber << 3) | wireType);
  }

  private static byte[] lengthDelimited(int fieldNumber, byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] t = tag(fieldNumber, 2);
    byte[] len = varint(data.length);
    out.write(t, 0, t.length);
    out.write(len, 0, len.length);
    out.write(data, 0, data.length);
    return out.toByteArray();
  }

  private static byte[] varintField(int fieldNumber, long value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] t = tag(fieldNumber, 0);
    byte[] v = varint(value);
    out.write(t, 0, t.length);
    out.write(v, 0, v.length);
    return out.toByteArray();
  }

  private static byte[] stringField(int fieldNumber, String value) {
    return lengthDelimited(fieldNumber, value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] concat(byte[]... arrays) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] a : arrays) {
      out.write(a, 0, a.length);
    }
    return out.toByteArray();
  }

  private static WireReader reader(byte[] data) {
    return new WireReader(data);
  }

  @Nested
  class LogMessageDecoding {

    @Test
    void allFields() throws IOException {
      byte[] data =
          concat(
              stringField(1, "2025-01-01T00:00:00Z"),
              varintField(2, 1234),
              varintField(3, 5678),
              varintField(4, 4),
              stringField(5, "MyApp"),
              stringField(6, "Something happened"));

      LogMessage msg = TombstoneDecoder.decodeLogMessage(reader(data));

      assertEquals("2025-01-01T00:00:00Z", msg.timestamp);
      assertEquals(1234, msg.pid);
      assertEquals(5678, msg.tid);
      assertEquals(4, msg.priority);
      assertEquals("MyApp", msg.tag);
      assertEquals("Something happened", msg.message);
    }
  }

  @Nested
  class LogBufferDecoding {

    @Test
    void nameAndMessages() throws IOException {
      byte[] logMsg = concat(stringField(1, "ts1"), stringField(6, "hello"));
      byte[] data = concat(stringField(1, "main"), lengthDelimited(2, logMsg));

      LogBuffer buf = TombstoneDecoder.decodeLogBuffer(reader(data));

      assertEquals("main", buf.name);
      assertEquals(1, buf.logs.size());
      assertEquals("hello", buf.logs.get(0).message);
    }

    @Test
    void multipleMessages() throws IOException {
      byte[] msg1 = concat(stringField(1, "ts1"), stringField(6, "first"));
      byte[] msg2 = concat(stringField(1, "ts2"), stringField(6, "second"));
      byte[] data =
          concat(stringField(1, "system"), lengthDelimited(2, msg1), lengthDelimited(2, msg2));

      LogBuffer buf = TombstoneDecoder.decodeLogBuffer(reader(data));

      assertEquals("system", buf.name);
      assertEquals(2, buf.logs.size());
      assertEquals("first", buf.logs.get(0).message);
      assertEquals("second", buf.logs.get(1).message);
    }
  }

  @Nested
  class CrashDetailDecoding {

    @Test
    void nameAndData() throws IOException {
      byte[] nameBytes = "detail_name".getBytes(StandardCharsets.UTF_8);
      byte[] dataBytes = "detail_data".getBytes(StandardCharsets.UTF_8);
      byte[] data = concat(lengthDelimited(1, nameBytes), lengthDelimited(2, dataBytes));

      CrashDetail detail = TombstoneDecoder.decodeCrashDetail(reader(data));

      assertArrayEquals(nameBytes, detail.name);
      assertArrayEquals(dataBytes, detail.data);
    }
  }

  @Nested
  class HeapObjectDecoding {

    @Test
    void allFields() throws IOException {
      byte[] frame = concat(varintField(1, 0x1000), stringField(6, "libtest.so"));
      byte[] data =
          concat(
              varintField(1, 0xDEADBEEF),
              varintField(2, 64),
              varintField(3, 100),
              lengthDelimited(4, frame),
              varintField(5, 200),
              lengthDelimited(6, frame));

      HeapObject obj = TombstoneDecoder.decodeHeapObject(reader(data));

      assertEquals(0xDEADBEEF, obj.address);
      assertEquals(64, obj.size);
      assertEquals(100, obj.allocationTid);
      assertEquals(1, obj.allocationBacktrace.size());
      assertEquals(0x1000, obj.allocationBacktrace.get(0).relPc);
      assertEquals(200, obj.deallocationTid);
      assertEquals(1, obj.deallocationBacktrace.size());
    }
  }

  @Nested
  class MemoryErrorDecoding {

    @Test
    void toolTypeAndHeap() throws IOException {
      byte[] frame = varintField(1, 0x1000);
      byte[] heapObj = concat(varintField(1, 0xFF), varintField(2, 32));
      byte[] data = concat(varintField(1, 1), varintField(2, 1), lengthDelimited(3, heapObj));

      MemoryError err = TombstoneDecoder.decodeMemoryError(reader(data));

      assertEquals(MemoryError.Tool.SCUDO, err.tool);
      assertEquals(MemoryError.Type.USE_AFTER_FREE, err.type);
      assertNotNull(err.heap);
      assertEquals(0xFF, err.heap.address);
      assertEquals(32, err.heap.size);
    }

    @Test
    void toolEnumValues() {
      assertEquals(MemoryError.Tool.GWP_ASAN, MemoryError.Tool.fromValue(0));
      assertEquals(MemoryError.Tool.SCUDO, MemoryError.Tool.fromValue(1));
      assertEquals(MemoryError.Tool.GWP_ASAN, MemoryError.Tool.fromValue(99));
      assertEquals(0, MemoryError.Tool.GWP_ASAN.getValue());
      assertEquals(1, MemoryError.Tool.SCUDO.getValue());
    }

    @Test
    void typeEnumValues() {
      assertEquals(MemoryError.Type.UNKNOWN, MemoryError.Type.fromValue(0));
      assertEquals(MemoryError.Type.USE_AFTER_FREE, MemoryError.Type.fromValue(1));
      assertEquals(MemoryError.Type.DOUBLE_FREE, MemoryError.Type.fromValue(2));
      assertEquals(MemoryError.Type.INVALID_FREE, MemoryError.Type.fromValue(3));
      assertEquals(MemoryError.Type.BUFFER_OVERFLOW, MemoryError.Type.fromValue(4));
      assertEquals(MemoryError.Type.BUFFER_UNDERFLOW, MemoryError.Type.fromValue(5));
      assertEquals(MemoryError.Type.UNKNOWN, MemoryError.Type.fromValue(99));
      assertEquals(0, MemoryError.Type.UNKNOWN.getValue());
      assertEquals(5, MemoryError.Type.BUFFER_UNDERFLOW.getValue());
    }
  }

  @Nested
  class StackHistoryBufferDecoding {

    @Test
    void tidAndEntries() throws IOException {
      byte[] frame = concat(varintField(1, 0x2000), stringField(4, "myFunc"));
      byte[] entry = concat(lengthDelimited(1, frame), varintField(2, 0xBEEF), varintField(3, 42));
      byte[] data = concat(varintField(1, 999), lengthDelimited(2, entry));

      StackHistoryBuffer shb = TombstoneDecoder.decodeStackHistoryBuffer(reader(data));

      assertEquals(999, shb.tid);
      assertEquals(1, shb.entries.size());
      assertNotNull(shb.entries.get(0).addr);
      assertEquals(0x2000, shb.entries.get(0).addr.relPc);
      assertEquals("myFunc", shb.entries.get(0).addr.functionName);
      assertEquals(0xBEEF, shb.entries.get(0).fp);
      assertEquals(42, shb.entries.get(0).tag);
    }
  }

  @Nested
  class StackHistoryBufferEntryDecoding {

    @Test
    void allFields() throws IOException {
      byte[] frame = varintField(1, 0x3000);
      byte[] data = concat(lengthDelimited(1, frame), varintField(2, 0xCAFE), varintField(3, 7));

      StackHistoryBufferEntry entry = TombstoneDecoder.decodeStackHistoryBufferEntry(reader(data));

      assertNotNull(entry.addr);
      assertEquals(0x3000, entry.addr.relPc);
      assertEquals(0xCAFE, entry.fp);
      assertEquals(7, entry.tag);
    }
  }

  @Nested
  class ArmMTEMetadataDecoding {

    @Test
    void memoryTags() throws IOException {
      byte[] tags = new byte[] {0x01, 0x02, 0x03};
      byte[] data = lengthDelimited(1, tags);

      ArmMTEMetadata mte = TombstoneDecoder.decodeArmMTEMetadata(reader(data));

      assertArrayEquals(tags, mte.memoryTags);
    }
  }

  @Nested
  class TombstoneHasSignal {

    @Test
    void noSignal() throws IOException {
      Tombstone t = TombstoneDecoder.decode(new byte[] {});
      assertFalse(t.hasSignal());
    }
  }

  @Nested
  class ArchitectureEnumCoverage {

    @Test
    void unknownFallsBackToNone() {
      assertEquals(Architecture.NONE, Architecture.fromValue(99));
    }

    @Test
    void riscv64Value() {
      assertEquals(4, Architecture.RISCV64.getValue());
    }
  }

  /** Tests that unknown fields are skipped in each sub-decoder. */
  @Nested
  class UnknownFieldSkipping {

    @Test
    void logMessage() throws IOException {
      byte[] data = concat(varintField(99, 1), stringField(6, "msg"));
      LogMessage msg = TombstoneDecoder.decodeLogMessage(reader(data));
      assertEquals("msg", msg.message);
    }

    @Test
    void logBuffer() throws IOException {
      byte[] data = concat(varintField(99, 1), stringField(1, "main"));
      LogBuffer buf = TombstoneDecoder.decodeLogBuffer(reader(data));
      assertEquals("main", buf.name);
    }

    @Test
    void crashDetail() throws IOException {
      byte[] data = concat(varintField(99, 1), lengthDelimited(1, new byte[] {0x42}));
      CrashDetail detail = TombstoneDecoder.decodeCrashDetail(reader(data));
      assertArrayEquals(new byte[] {0x42}, detail.name);
    }

    @Test
    void heapObject() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 0xFF));
      HeapObject obj = TombstoneDecoder.decodeHeapObject(reader(data));
      assertEquals(0xFF, obj.address);
    }

    @Test
    void memoryError() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 1));
      MemoryError err = TombstoneDecoder.decodeMemoryError(reader(data));
      assertEquals(MemoryError.Tool.SCUDO, err.tool);
    }

    @Test
    void stackHistoryBuffer() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 42));
      StackHistoryBuffer shb = TombstoneDecoder.decodeStackHistoryBuffer(reader(data));
      assertEquals(42, shb.tid);
    }

    @Test
    void stackHistoryBufferEntry() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(2, 0xBEEF));
      StackHistoryBufferEntry entry = TombstoneDecoder.decodeStackHistoryBufferEntry(reader(data));
      assertEquals(0xBEEF, entry.fp);
    }

    @Test
    void armMteMetadata() throws IOException {
      byte[] data = concat(varintField(99, 1), lengthDelimited(1, new byte[] {0x01}));
      ArmMTEMetadata mte = TombstoneDecoder.decodeArmMTEMetadata(reader(data));
      assertArrayEquals(new byte[] {0x01}, mte.memoryTags);
    }

    @Test
    void signal() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 11));
      Signal sig = TombstoneDecoder.decodeSignal(reader(data));
      assertEquals(11, sig.number);
    }

    @Test
    void thread() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 42));
      TombstoneThread thread = TombstoneDecoder.decodeThread(reader(data));
      assertEquals(42, thread.id);
    }

    @Test
    void threadMapEntry() throws IOException {
      java.util.Map<Integer, TombstoneThread> threads = new java.util.HashMap<>();
      byte[] threadMsg = varintField(1, 1);
      byte[] data = concat(varintField(99, 1), varintField(1, 1), lengthDelimited(2, threadMsg));
      TombstoneDecoder.decodeThreadMapEntry(reader(data), threads);
      assertEquals(1, threads.size());
    }

    @Test
    void backtraceFrame() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 0x1000));
      BacktraceFrame frame = TombstoneDecoder.decodeBacktraceFrame(reader(data));
      assertEquals(0x1000, frame.relPc);
    }

    @Test
    void register() throws IOException {
      byte[] data = concat(varintField(99, 1), stringField(1, "x0"));
      Register reg = TombstoneDecoder.decodeRegister(reader(data));
      assertEquals("x0", reg.name);
    }

    @Test
    void memoryMapping() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 0x1000));
      MemoryMapping mapping = TombstoneDecoder.decodeMemoryMapping(reader(data));
      assertEquals(0x1000, mapping.beginAddress);
    }

    @Test
    void memoryDump() throws IOException {
      byte[] data = concat(varintField(99, 1), stringField(1, "x0"));
      MemoryDump dump = TombstoneDecoder.decodeMemoryDump(reader(data));
      assertEquals("x0", dump.registerName);
    }

    @Test
    void memoryDumpWithArmMteMetadata() throws IOException {
      byte[] mte = lengthDelimited(1, new byte[] {0x01});
      byte[] data = concat(varintField(99, 1), stringField(1, "x0"), lengthDelimited(6, mte));
      MemoryDump dump = TombstoneDecoder.decodeMemoryDump(reader(data));
      assertNotNull(dump.armMteMetadata);
      assertArrayEquals(new byte[] {0x01}, dump.armMteMetadata.memoryTags);
    }

    @Test
    void cause() throws IOException {
      byte[] data = concat(varintField(99, 1), stringField(1, "oops"));
      Cause cause = TombstoneDecoder.decodeCause(reader(data));
      assertEquals("oops", cause.humanReadable);
    }

    @Test
    void causeWithMemoryError() throws IOException {
      byte[] memErr = varintField(1, 1);
      byte[] data = concat(varintField(99, 1), stringField(1, "oops"), lengthDelimited(2, memErr));
      Cause cause = TombstoneDecoder.decodeCause(reader(data));
      assertEquals("oops", cause.humanReadable);
      assertNotNull(cause.memoryError);
      assertEquals(MemoryError.Tool.SCUDO, cause.memoryError.tool);
    }

    @Test
    void fd() throws IOException {
      byte[] data = concat(varintField(99, 1), varintField(1, 3));
      FD fd = TombstoneDecoder.decodeFD(reader(data));
      assertEquals(3, fd.fd);
    }
  }

  /** Tests for tombstone-level paths not covered by real snapshots. */
  @Nested
  class TombstoneLevelPaths {

    @Test
    void crashDetailsAtTombstoneLevel() throws IOException {
      byte[] detail = concat(lengthDelimited(1, "name".getBytes(StandardCharsets.UTF_8)));
      byte[] data = lengthDelimited(21, detail);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.crashDetails.size());
    }

    @Test
    void guestThreadsAtTombstoneLevel() throws IOException {
      byte[] threadMsg = concat(varintField(1, 50), stringField(2, "guest"));
      byte[] mapEntry = concat(varintField(1, 50), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(25, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.guestThreads.size());
      assertEquals("guest", t.guestThreads.get(50).name);
    }

    @Test
    void logBuffersAtTombstoneLevel() throws IOException {
      byte[] logMsg = stringField(6, "hello");
      byte[] logBuf = concat(stringField(1, "main"), lengthDelimited(2, logMsg));
      byte[] data = lengthDelimited(18, logBuf);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.logBuffers.size());
    }

    @Test
    void stackHistoryBufferAtTombstoneLevel() throws IOException {
      byte[] shb = varintField(1, 123);
      byte[] data = lengthDelimited(26, shb);
      Tombstone t = TombstoneDecoder.decode(data);
      assertNotNull(t.stackHistoryBuffer);
      assertEquals(123, t.stackHistoryBuffer.tid);
    }
  }

  /** Tests for signal fields not present in real snapshots. */
  @Nested
  class SignalFieldPaths {

    @Test
    void senderFields() throws IOException {
      byte[] signalMsg =
          concat(
              varintField(1, 9),
              stringField(2, "SIGKILL"),
              varintField(5, 1), // hasSender
              varintField(6, 1000), // senderUid
              varintField(7, 4321)); // senderPid
      byte[] data = lengthDelimited(10, signalMsg);
      Tombstone t = TombstoneDecoder.decode(data);
      assertNotNull(t.signal);
      assertTrue(t.signal.hasSender);
      assertEquals(1000, t.signal.senderUid);
      assertEquals(4321, t.signal.senderPid);
    }

    @Test
    void faultAdjacentMetadata() throws IOException {
      byte[] memDump = concat(stringField(1, "x0"), varintField(3, 0x1000));
      byte[] signalMsg =
          concat(varintField(1, 11), stringField(2, "SIGSEGV"), lengthDelimited(10, memDump));
      byte[] data = lengthDelimited(10, signalMsg);
      Tombstone t = TombstoneDecoder.decode(data);
      assertNotNull(t.signal);
      assertNotNull(t.signal.faultAdjacentMetadata);
      assertEquals("x0", t.signal.faultAdjacentMetadata.registerName);
    }
  }

  /** Tests for thread sub-fields not covered by snapshots. */
  @Nested
  class ThreadFieldPaths {

    @Test
    void backtraceNoteAndUnreadableElfFiles() throws IOException {
      byte[] threadMsg =
          concat(
              varintField(1, 1),
              stringField(7, "note1"), // backtraceNote
              stringField(9, "bad.so")); // unreadableElfFiles
      byte[] mapEntry = concat(varintField(1, 1), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.get(1).backtraceNote.size());
      assertEquals("note1", t.threads.get(1).backtraceNote.get(0));
      assertEquals(1, t.threads.get(1).unreadableElfFiles.size());
      assertEquals("bad.so", t.threads.get(1).unreadableElfFiles.get(0));
    }

    @Test
    void taggedAddrCtrlAndPacEnabledKeys() throws IOException {
      byte[] threadMsg = concat(varintField(1, 1), varintField(6, 0xAB), varintField(8, 0xCD));
      byte[] mapEntry = concat(varintField(1, 1), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(0xAB, t.threads.get(1).taggedAddrCtrl);
      assertEquals(0xCD, t.threads.get(1).pacEnabledKeys);
    }
  }
}
