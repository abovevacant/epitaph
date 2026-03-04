package com.abovevacant.epitaph.wire;

import static org.junit.jupiter.api.Assertions.*;

import com.abovevacant.epitaph.core.Architecture;
import com.abovevacant.epitaph.core.Tombstone;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TombstoneDecoderEdgeCaseTest {

  // --- Helper: protobuf encoding primitives ---

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
    return lengthDelimited(fieldNumber, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static byte[] concat(byte[]... arrays) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] a : arrays) {
      out.write(a, 0, a.length);
    }
    return out.toByteArray();
  }

  // --- Empty / minimal tombstone ---

  @Nested
  class EmptyTombstone {

    @Test
    void emptyInputProducesDefaults() throws IOException {
      Tombstone t = TombstoneDecoder.decode(new byte[] {});

      assertEquals(Architecture.NONE, t.arch);
      assertEquals(Architecture.NONE, t.guestArch);
      assertEquals("", t.buildFingerprint);
      assertEquals("", t.revision);
      assertEquals("", t.timestamp);
      assertEquals(0, t.pid);
      assertEquals(0, t.tid);
      assertEquals(0, t.uid);
      assertEquals("", t.selinuxLabel);
      assertTrue(t.commandLine.isEmpty());
      assertEquals(0, t.processUptime);
      assertNull(t.signal);
      assertEquals("", t.abortMessage);
      assertTrue(t.crashDetails.isEmpty());
      assertTrue(t.causes.isEmpty());
      assertTrue(t.threads.isEmpty());
      assertTrue(t.guestThreads.isEmpty());
      assertTrue(t.memoryMappings.isEmpty());
      assertTrue(t.logBuffers.isEmpty());
      assertTrue(t.openFds.isEmpty());
      assertEquals(0, t.pageSize);
      assertFalse(t.hasBeen16kbMode);
      assertNull(t.stackHistoryBuffer);
    }

    @Test
    void minimalTombstoneWithOnlyArch() throws IOException {
      // arch = ARM64 (1)
      byte[] data = varintField(1, 1);
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(Architecture.ARM64, t.arch);
      assertEquals(0, t.pid);
    }
  }

  // --- Unknown fields are skipped ---

  @Nested
  class UnknownFieldSkipping {

    @Test
    void unknownVarintFieldSkipped() throws IOException {
      // field 99 (varint) = 42, then field 5 (pid) = 123
      byte[] data = concat(varintField(99, 42), varintField(5, 123));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(123, t.pid);
    }

    @Test
    void unknownLengthDelimitedFieldSkipped() throws IOException {
      // field 99 (length-delimited) = "garbage", then field 5 (pid) = 456
      byte[] data = concat(stringField(99, "garbage"), varintField(5, 456));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(456, t.pid);
    }

    @Test
    void unknownFixed64FieldSkipped() throws IOException {
      // field 99 wire type 1 (fixed64), 8 bytes of data, then pid=789
      byte[] tagBytes = tag(99, 1);
      byte[] fixed = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
      byte[] data = concat(tagBytes, fixed, varintField(5, 789));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(789, t.pid);
    }

    @Test
    void unknownFixed32FieldSkipped() throws IOException {
      // field 99 wire type 5 (fixed32), 4 bytes, then pid=101
      byte[] tagBytes = tag(99, 5);
      byte[] fixed = new byte[] {1, 2, 3, 4};
      byte[] data = concat(tagBytes, fixed, varintField(5, 101));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals(101, t.pid);
    }

    @Test
    void multipleUnknownFieldsSkipped() throws IOException {
      byte[] data =
          concat(
              varintField(99, 1),
              stringField(98, "skip me"),
              varintField(97, 2),
              stringField(2, "fp-123"),
              varintField(96, 3));
      Tombstone t = TombstoneDecoder.decode(data);
      assertEquals("fp-123", t.buildFingerprint);
    }
  }

  // --- Wire type mismatch ---

  @Nested
  class WireTypeMismatch {

    @Test
    void varintFieldWithLengthDelimitedWireType() {
      // pid (field 5) should be varint (wire type 0), but encode as length-delimited (wire type 2)
      byte[] data = stringField(5, "not a number");
      IOException ex = assertThrows(IOException.class, () -> TombstoneDecoder.decode(data));
      assertTrue(ex.getMessage().contains("Field 5"));
      assertTrue(ex.getMessage().contains("varint"));
      assertTrue(ex.getMessage().contains("length-delimited"));
    }

    @Test
    void stringFieldWithVarintWireType() {
      // buildFingerprint (field 2) should be length-delimited, but encode as varint
      byte[] data = varintField(2, 42);
      IOException ex = assertThrows(IOException.class, () -> TombstoneDecoder.decode(data));
      assertTrue(ex.getMessage().contains("Field 2"));
      assertTrue(ex.getMessage().contains("length-delimited"));
      assertTrue(ex.getMessage().contains("varint"));
    }

    @Test
    void messageFieldWithVarintWireType() {
      // signal (field 10) should be length-delimited, but encode as varint
      byte[] data = varintField(10, 42);
      IOException ex = assertThrows(IOException.class, () -> TombstoneDecoder.decode(data));
      assertTrue(ex.getMessage().contains("Field 10"));
    }
  }

  // --- Truncated / malformed input ---

  @Nested
  class MalformedInput {

    @Test
    void truncatedTagVarint() {
      // Tag starts with continuation bit but no more bytes
      byte[] data = new byte[] {(byte) 0x80};
      assertThrows(EOFException.class, () -> TombstoneDecoder.decode(data));
    }

    @Test
    void truncatedFieldValue() {
      // Tag for field 2 (string), length=100 but no data follows
      byte[] data = new byte[] {0x12, 0x64};
      assertThrows(EOFException.class, () -> TombstoneDecoder.decode(data));
    }

    @Test
    void truncatedVarintValue() {
      // Tag for field 5 (varint), then a continuation byte with no termination
      byte[] data = new byte[] {0x28, (byte) 0x80};
      assertThrows(EOFException.class, () -> TombstoneDecoder.decode(data));
    }

    @Test
    void truncatedEmbeddedMessage() {
      // Tag for signal (field 10), length=5, only 2 bytes of message
      byte[] data = new byte[] {0x52, 0x05, 0x08, 0x0B};
      assertThrows(EOFException.class, () -> TombstoneDecoder.decode(data));
    }

    @Test
    void unknownWireTypeInData() {
      // field 99, wire type 3 (start group — deprecated, unsupported)
      byte[] tagBytes = tag(99, 3);
      assertThrows(IOException.class, () -> TombstoneDecoder.decode(tagBytes));
    }

    @Test
    void truncatedStringLength() {
      // field 2 (string), length varint is truncated (continuation bit set, no more bytes)
      byte[] data = new byte[] {0x12, (byte) 0x80};
      assertThrows(EOFException.class, () -> TombstoneDecoder.decode(data));
    }
  }

  // --- Round-trip: known fields parsed correctly ---

  @Nested
  class KnownFields {

    @Test
    void allScalarFields() throws IOException {
      byte[] data =
          concat(
              varintField(1, 1), // arch = ARM64
              stringField(2, "google/device/1.0"), // buildFingerprint
              stringField(3, "rev1"), // revision
              stringField(4, "2025-01-01T00:00:00Z"), // timestamp
              varintField(5, 1234), // pid
              varintField(6, 5678), // tid
              varintField(7, 10042), // uid
              stringField(8, "u:r:untrusted_app:s0"), // selinuxLabel
              stringField(9, "/system/bin/app_process64"), // commandLine[0]
              stringField(9, "com.example.app"), // commandLine[1]
              varintField(20, 300), // processUptime
              stringField(14, "Abort message"), // abortMessage
              varintField(22, 4096), // pageSize
              varintField(23, 1)); // hasBeen16kbMode

      Tombstone t = TombstoneDecoder.decode(data);

      assertEquals(Architecture.ARM64, t.arch);
      assertEquals("google/device/1.0", t.buildFingerprint);
      assertEquals("rev1", t.revision);
      assertEquals("2025-01-01T00:00:00Z", t.timestamp);
      assertEquals(1234, t.pid);
      assertEquals(5678, t.tid);
      assertEquals(10042, t.uid);
      assertEquals("u:r:untrusted_app:s0", t.selinuxLabel);
      assertEquals(2, t.commandLine.size());
      assertEquals("/system/bin/app_process64", t.commandLine.get(0));
      assertEquals("com.example.app", t.commandLine.get(1));
      assertEquals(300, t.processUptime);
      assertEquals("Abort message", t.abortMessage);
      assertEquals(4096, t.pageSize);
      assertTrue(t.hasBeen16kbMode);
    }

    @Test
    void signalEmbeddedMessage() throws IOException {
      // Build a Signal message: number=11, name="SIGSEGV", code=1, codeName="SEGV_MAPERR"
      byte[] signalMsg =
          concat(
              varintField(1, 11),
              stringField(2, "SIGSEGV"),
              varintField(3, 1),
              stringField(4, "SEGV_MAPERR"),
              varintField(8, 1), // hasFaultAddress
              varintField(9, 0xDEAD)); // faultAddress
      byte[] data = lengthDelimited(10, signalMsg);

      Tombstone t = TombstoneDecoder.decode(data);

      assertNotNull(t.signal);
      assertEquals(11, t.signal.number);
      assertEquals("SIGSEGV", t.signal.name);
      assertEquals(1, t.signal.code);
      assertEquals("SEGV_MAPERR", t.signal.codeName);
      assertTrue(t.signal.hasFaultAddress);
      assertEquals(0xDEAD, t.signal.faultAddress);
    }

    @Test
    void causeEmbeddedMessage() throws IOException {
      byte[] causeMsg = stringField(1, "null pointer dereference");
      byte[] data = lengthDelimited(15, causeMsg);

      Tombstone t = TombstoneDecoder.decode(data);

      assertEquals(1, t.causes.size());
      assertEquals("null pointer dereference", t.causes.get(0).humanReadable);
    }

    @Test
    void multipleCauses() throws IOException {
      byte[] cause1 = lengthDelimited(15, stringField(1, "cause one"));
      byte[] cause2 = lengthDelimited(15, stringField(1, "cause two"));
      byte[] data = concat(cause1, cause2);

      Tombstone t = TombstoneDecoder.decode(data);

      assertEquals(2, t.causes.size());
      assertEquals("cause one", t.causes.get(0).humanReadable);
      assertEquals("cause two", t.causes.get(1).humanReadable);
    }

    @Test
    void threadMapEntry() throws IOException {
      // Thread message: id=100, name="main"
      byte[] threadMsg = concat(varintField(1, 100), stringField(2, "main"));
      // Map entry: key=100, value=threadMsg
      byte[] mapEntry = concat(varintField(1, 100), lengthDelimited(2, threadMsg));
      byte[] data = lengthDelimited(16, mapEntry);

      Tombstone t = TombstoneDecoder.decode(data);

      assertEquals(1, t.threads.size());
      assertTrue(t.threads.containsKey(100));
      assertEquals("main", t.threads.get(100).name);
      assertEquals(100, t.threads.get(100).id);
    }
  }
}
