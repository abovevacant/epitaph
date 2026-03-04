package com.abovevacant.epitaph.wire;

import static org.junit.jupiter.api.Assertions.*;

import com.abovevacant.epitaph.core.Architecture;
import com.abovevacant.epitaph.core.Tombstone;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests that challenge TombstoneDecoder behavior at boundaries, probing for actual bugs rather than
 * confirming existing behavior.
 */
class TombstoneDecoderBehaviorTest {

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
    write(out, tag(fieldNumber, 2));
    write(out, varint(data.length));
    write(out, data);
    return out.toByteArray();
  }

  private static byte[] varintField(int fieldNumber, long value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write(out, tag(fieldNumber, 0));
    write(out, varint(value));
    return out.toByteArray();
  }

  private static byte[] stringField(int fieldNumber, String value) {
    return lengthDelimited(fieldNumber, value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] concat(byte[]... arrays) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] a : arrays) {
      write(out, a);
    }
    return out.toByteArray();
  }

  private static void write(ByteArrayOutputStream out, byte[] data) {
    out.write(data, 0, data.length);
  }

  // --- Duplicate fields: which value wins? ---

  @Nested
  class DuplicateFields {

    @Test
    void duplicateScalarLastWins() throws IOException {
      // pid set twice: 100, then 200. Protobuf spec says last value wins.
      byte[] data = concat(varintField(5, 100), varintField(5, 200));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(200, t.pid);
    }

    @Test
    void duplicateStringLastWins() throws IOException {
      // buildFingerprint set twice
      byte[] data = concat(stringField(2, "first"), stringField(2, "second"));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals("second", t.buildFingerprint);
    }

    @Test
    void duplicateMessageLastWins() throws IOException {
      // signal set twice — second should replace first
      byte[] signal1 = concat(varintField(1, 11), stringField(2, "SIGSEGV"));
      byte[] signal2 = concat(varintField(1, 6), stringField(2, "SIGABRT"));
      byte[] data = concat(lengthDelimited(10, signal1), lengthDelimited(10, signal2));
      Tombstone t = TombstoneDecoder.decode(data);
      assertNotNull(t.signal);
      assertEquals(6, t.signal.number);
      assertEquals("SIGABRT", t.signal.name);
    }

    @Test
    void duplicateBoolLastWins() throws IOException {
      // hasBeen16kbMode: true, then false
      byte[] data = concat(varintField(23, 1), varintField(23, 0));
      Tombstone t = TombstoneDecoder.decode(data);
      assertFalse(t.hasBeen16kbMode);
    }
  }

  // --- Repeated fields: duplicates accumulate ---

  @Nested
  class RepeatedFields {

    @Test
    void repeatedCausesAccumulate() throws IOException {
      byte[] cause1 = lengthDelimited(15, stringField(1, "cause A"));
      byte[] cause2 = lengthDelimited(15, stringField(1, "cause B"));
      byte[] cause3 = lengthDelimited(15, stringField(1, "cause C"));
      byte[] data = concat(cause1, cause2, cause3);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(3, t.causes.size());
      assertEquals("cause A", t.causes.get(0).humanReadable);
      assertEquals("cause C", t.causes.get(2).humanReadable);
    }

    @Test
    void repeatedCommandLinesAccumulate() throws IOException {
      byte[] data = concat(stringField(9, "arg0"), stringField(9, "arg1"), stringField(9, "arg2"));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(3, t.commandLine.size());
    }
  }

  // --- Empty embedded messages ---

  @Nested
  class EmptyEmbeddedMessages {

    @Test
    void emptySignalMessage() throws IOException {
      // Signal with length=0: all fields should be defaults
      byte[] data = lengthDelimited(10, new byte[] {});
      Tombstone t = TombstoneDecoder.decode(data);
      assertNotNull(t.signal);
      assertEquals(0, t.signal.number);
      assertEquals("", t.signal.name);
      assertEquals(0, t.signal.code);
      assertEquals("", t.signal.codeName);
      assertFalse(t.signal.hasSender);
      assertFalse(t.signal.hasFaultAddress);
    }

    @Test
    void emptyCauseMessage() throws IOException {
      byte[] data = lengthDelimited(15, new byte[] {});
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.causes.size());
      assertEquals("", t.causes.get(0).humanReadable);
      assertNull(t.causes.get(0).memoryError);
    }

    @Test
    void emptyThreadInMapEntry() throws IOException {
      // Map entry with key=1, value=empty thread message
      byte[] mapEntry = concat(varintField(1, 1), lengthDelimited(2, new byte[] {}));
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.size());
      assertEquals(0, t.threads.get(1).id);
      assertEquals("", t.threads.get(1).name);
      assertTrue(t.threads.get(1).registers.isEmpty());
      assertTrue(t.threads.get(1).backtrace.isEmpty());
    }
  }

  // --- Map entry edge cases ---

  @Nested
  class MapEntryEdgeCases {

    @Test
    void mapEntryWithNoValueIsIgnored() throws IOException {
      // Map entry with only a key, no value message
      byte[] mapEntry = varintField(1, 42);
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      // Value is null, so it should NOT be put in the map
      assertTrue(t.threads.isEmpty());
    }

    @Test
    void mapEntryWithNoKeyDefaultsToZero() throws IOException {
      // Map entry with only a value, no key — key defaults to 0
      byte[] threadMsg = concat(varintField(1, 77), stringField(2, "worker"));
      byte[] mapEntry = lengthDelimited(2, threadMsg);
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.size());
      assertTrue(t.threads.containsKey(0));
      assertEquals("worker", t.threads.get(0).name);
    }

    @Test
    void duplicateMapKeysLastWins() throws IOException {
      // Two entries for key 5 — second should overwrite first
      byte[] thread1 = concat(varintField(1, 5), stringField(2, "first"));
      byte[] entry1 = concat(varintField(1, 5), lengthDelimited(2, thread1));
      byte[] thread2 = concat(varintField(1, 5), stringField(2, "second"));
      byte[] entry2 = concat(varintField(1, 5), lengthDelimited(2, thread2));
      byte[] data = concat(lengthDelimited(16, entry1), lengthDelimited(16, entry2));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.size());
      assertEquals("second", t.threads.get(5).name);
    }

    @Test
    void emptyMapEntry() throws IOException {
      // Completely empty map entry message — key defaults to 0, value is null → ignored
      byte[] data = lengthDelimited(16, new byte[] {});
      Tombstone t = TombstoneDecoder.decode(data);
      assertTrue(t.threads.isEmpty());
    }
  }

  // --- Field ordering ---

  @Nested
  class FieldOrdering {

    @Test
    void fieldsInReverseOrderStillWork() throws IOException {
      // Protobuf doesn't require fields in order
      byte[] data =
          concat(
              varintField(22, 16384), // pageSize
              stringField(14, "crashed"), // abortMessage
              stringField(8, "label"), // selinuxLabel
              varintField(7, 1000), // uid
              varintField(6, 99), // tid
              varintField(5, 42), // pid
              stringField(2, "fp"), // buildFingerprint
              varintField(1, 3)); // arch = X86_64
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(Architecture.X86_64, t.arch);
      assertEquals("fp", t.buildFingerprint);
      assertEquals(42, t.pid);
      assertEquals(99, t.tid);
      assertEquals(1000, t.uid);
      assertEquals("label", t.selinuxLabel);
      assertEquals("crashed", t.abortMessage);
      assertEquals(16384, t.pageSize);
    }

    @Test
    void scalarFieldsInterleavedWithRepeated() throws IOException {
      // Mix scalar and repeated fields in non-standard order
      byte[] data =
          concat(
              stringField(9, "cmd1"), // commandLine
              varintField(5, 10), // pid
              stringField(9, "cmd2"), // commandLine
              lengthDelimited(15, stringField(1, "cause")), // causes
              varintField(5, 20), // pid again (overwrites)
              stringField(9, "cmd3")); // commandLine
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(20, t.pid); // last wins
      assertEquals(3, t.commandLine.size());
      assertEquals(1, t.causes.size());
    }
  }

  // --- Architecture enum edge cases ---

  @Nested
  class ArchitectureEdgeCases {

    @Test
    void unknownArchValueDefaultsToNone() throws IOException {
      // arch = 999 — not a valid enum value
      byte[] data = varintField(1, 999);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(Architecture.NONE, t.arch);
    }

    @Test
    void negativeArchValueDefaultsToNone() throws IOException {
      // Varint that decodes to negative int32
      byte[] data = varintField(1, 0xFFFFFFFFL); // 4294967295 → -1 as int32
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(Architecture.NONE, t.arch);
    }
  }

  // --- Zero byte in stream ---

  @Nested
  class ZeroByteInStream {

    @Test
    void zeroByteCausesEarlyTermination() throws IOException {
      // Set pid=42, then a zero byte (tag=0), then uid=99
      // The zero byte should terminate parsing — uid should remain at default
      byte[] pidField = varintField(5, 42);
      byte[] zeroByte = new byte[] {0x00};
      byte[] uidField = varintField(7, 99);
      byte[] data = concat(pidField, zeroByte, uidField);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(42, t.pid);
      assertEquals(0, t.uid); // uid never parsed — silently lost
    }
  }

  // --- Large values ---

  @Nested
  class LargeValues {

    @Test
    void faultAddressFullUint64() throws IOException {
      // Signal with faultAddress = 0xDEADBEEFCAFEBABE
      long addr = 0xDEADBEEFCAFEBABEL;
      byte[] signalMsg = concat(varintField(8, 1), varintField(9, addr));
      byte[] data = lengthDelimited(10, signalMsg);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(addr, t.signal.faultAddress);
    }

    @Test
    void pidFromLargeVarintTruncatesToInt() throws IOException {
      // pid field is int, but varint encodes 0x1_0000_0001 (4294967297).
      // readVarInt32 casts to int → 1. This matches standard protobuf behavior:
      // protobuf-java's readInt32() does the same cast. Can't happen with valid data
      // since Android PIDs max out at 4194304.
      byte[] data = varintField(5, 0x1_0000_0001L);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.pid);
    }
  }

  // --- Nested message with trailing data in parent ---

  @Nested
  class NestedMessageBoundaries {

    @Test
    void embeddedMessageDoesNotConsumeParentData() throws IOException {
      // Signal message followed by pid field — signal sub-reader must not eat the pid bytes
      byte[] signalMsg = varintField(1, 11); // signal number
      byte[] data = concat(lengthDelimited(10, signalMsg), varintField(5, 555));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(11, t.signal.number);
      assertEquals(555, t.pid);
    }

    @Test
    void embeddedMessageWithExtraUnknownFieldsInsideIsContained() throws IOException {
      // Signal message with an unknown field 99 inside — it should be skipped within the
      // sub-reader, and not bleed into the parent
      byte[] signalMsg =
          concat(varintField(1, 11), varintField(99, 12345), stringField(2, "SIGSEGV"));
      byte[] data = concat(lengthDelimited(10, signalMsg), varintField(5, 777));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(11, t.signal.number);
      assertEquals("SIGSEGV", t.signal.name);
      assertEquals(777, t.pid);
    }
  }

  // --- Thread with all sub-structures ---

  @Nested
  class ThreadSubStructures {

    @Test
    void threadWithBacktraceFrameDefaults() throws IOException {
      // Backtrace frame that's empty — all fields should be defaults
      byte[] frame = new byte[] {};
      byte[] threadMsg = concat(varintField(1, 1), lengthDelimited(4, frame));
      byte[] mapEntry = concat(varintField(1, 1), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.get(1).backtrace.size());
      assertEquals(0, t.threads.get(1).backtrace.get(0).pc);
      assertEquals(0, t.threads.get(1).backtrace.get(0).relPc);
      assertEquals("", t.threads.get(1).backtrace.get(0).functionName);
      assertEquals("", t.threads.get(1).backtrace.get(0).fileName);
    }

    @Test
    void threadWithEmptyRegister() throws IOException {
      byte[] registerMsg = new byte[] {};
      byte[] threadMsg = concat(varintField(1, 2), lengthDelimited(3, registerMsg));
      byte[] mapEntry = concat(varintField(1, 2), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.get(2).registers.size());
      assertEquals("", t.threads.get(2).registers.get(0).name);
      assertEquals(0, t.threads.get(2).registers.get(0).value);
    }

    @Test
    void threadWithEmptyMemoryDump() throws IOException {
      byte[] dumpMsg = new byte[] {};
      byte[] threadMsg = concat(varintField(1, 3), lengthDelimited(5, dumpMsg));
      byte[] mapEntry = concat(varintField(1, 3), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(16, mapEntry);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(1, t.threads.get(3).memoryDump.size());
      assertEquals("", t.threads.get(3).memoryDump.get(0).registerName);
      assertEquals(0, t.threads.get(3).memoryDump.get(0).memory.length);
    }
  }
}
