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
}
