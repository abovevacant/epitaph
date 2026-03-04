package com.abovevacant.epitaph.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WireReaderTest {

  // --- Varint ---

  @Nested
  class Varint {

    @Test
    void zero() throws IOException {
      WireReader r = new WireReader(new byte[] {0x00});
      assertEquals(0, r.readVarInt());
      assertFalse(r.hasRemaining());
    }

    @Test
    void singleByte() throws IOException {
      WireReader r = new WireReader(new byte[] {0x01});
      assertEquals(1, r.readVarInt());
    }

    @Test
    void maxSingleByte() throws IOException {
      WireReader r = new WireReader(new byte[] {0x7F});
      assertEquals(127, r.readVarInt());
    }

    @Test
    void twoByte() throws IOException {
      // 300 = 0b100101100 -> 0xAC 0x02
      WireReader r = new WireReader(new byte[] {(byte) 0xAC, 0x02});
      assertEquals(300, r.readVarInt());
    }

    @Test
    void maxUint32() throws IOException {
      // 0xFFFFFFFF = 4294967295
      WireReader r =
          new WireReader(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F});
      assertEquals(0xFFFFFFFFL, r.readVarInt());
    }

    @Test
    void maxUint64() throws IOException {
      // 0xFFFFFFFFFFFFFFFF encoded as 10 bytes of 0xFF, last byte 0x01
      WireReader r =
          new WireReader(
              new byte[] {
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                0x01
              });
      assertEquals(-1L, r.readVarInt()); // all bits set = -1 as signed long
    }

    @Test
    void negativeOneAsSigned() throws IOException {
      // protobuf encodes -1 as 10 bytes (sign-extended varint)
      WireReader r =
          new WireReader(
              new byte[] {
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                0x01
              });
      assertEquals(-1, r.readVarInt32());
    }

    @Test
    void truncatedVarintThrows() {
      // Continuation bit set but no more bytes
      WireReader r = new WireReader(new byte[] {(byte) 0x80});
      assertThrows(EOFException.class, r::readVarInt);
    }

    @Test
    void truncatedMultiByteVarintThrows() {
      // Two continuation bytes, then EOF
      WireReader r = new WireReader(new byte[] {(byte) 0x80, (byte) 0x80});
      assertThrows(EOFException.class, r::readVarInt);
    }

    @Test
    void malformedVarintTooLongThrows() {
      // 11 continuation bytes — exceeds 64-bit capacity
      WireReader r =
          new WireReader(
              new byte[] {
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                0x01
              });
      IOException ex = assertThrows(IOException.class, r::readVarInt);
      assertTrue(ex.getMessage().contains("Malformed varint"));
    }

    @Test
    void emptyBufferThrows() {
      WireReader r = new WireReader(new byte[] {});
      assertThrows(EOFException.class, r::readVarInt);
    }

    @Test
    void readMultipleSequentially() throws IOException {
      // 1, 300, 0
      WireReader r = new WireReader(new byte[] {0x01, (byte) 0xAC, 0x02, 0x00});
      assertEquals(1, r.readVarInt());
      assertEquals(300, r.readVarInt());
      assertEquals(0, r.readVarInt());
      assertFalse(r.hasRemaining());
    }

    @Test
    void boolTrueAndFalse() throws IOException {
      WireReader r = new WireReader(new byte[] {0x01, 0x00});
      assertTrue(r.readBool());
      assertFalse(r.readBool());
    }

    @Test
    void boolNonOneIsTrue() throws IOException {
      WireReader r = new WireReader(new byte[] {0x42});
      assertTrue(r.readBool());
    }
  }

  // --- Fixed ---

  @Nested
  class Fixed {

    @Test
    void fixed32LittleEndian() throws IOException {
      // 0x04030201
      WireReader r = new WireReader(new byte[] {0x01, 0x02, 0x03, 0x04});
      assertEquals(0x04030201, r.readFixed32());
    }

    @Test
    void fixed64LittleEndian() throws IOException {
      WireReader r = new WireReader(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
      assertEquals(0x0807060504030201L, r.readFixed64());
    }

    @Test
    void fixed32TruncatedThrows() {
      WireReader r = new WireReader(new byte[] {0x01, 0x02, 0x03});
      assertThrows(EOFException.class, r::readFixed32);
    }

    @Test
    void fixed64TruncatedThrows() {
      WireReader r = new WireReader(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
      assertThrows(EOFException.class, r::readFixed64);
    }

    @Test
    void fixed32AllOnes() throws IOException {
      WireReader r =
          new WireReader(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
      assertEquals(-1, r.readFixed32());
    }

    @Test
    void fixed64AllOnes() throws IOException {
      WireReader r =
          new WireReader(
              new byte[] {
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
              });
      assertEquals(-1L, r.readFixed64());
    }
  }

  // --- Length-delimited ---

  @Nested
  class LengthDelimited {

    @Test
    void readBytes() throws IOException {
      // length=3, then 3 bytes
      WireReader r = new WireReader(new byte[] {0x03, 0x41, 0x42, 0x43});
      assertArrayEquals(new byte[] {0x41, 0x42, 0x43}, r.readBytes());
    }

    @Test
    void readEmptyBytes() throws IOException {
      WireReader r = new WireReader(new byte[] {0x00});
      assertArrayEquals(new byte[] {}, r.readBytes());
    }

    @Test
    void readString() throws IOException {
      // length=5, "hello"
      WireReader r = new WireReader(new byte[] {0x05, 0x68, 0x65, 0x6C, 0x6C, 0x6F});
      assertEquals("hello", r.readString());
    }

    @Test
    void readEmptyString() throws IOException {
      WireReader r = new WireReader(new byte[] {0x00});
      assertEquals("", r.readString());
    }

    @Test
    void readStringUtf8() throws IOException {
      // "ä" = 0xC3 0xA4 in UTF-8
      WireReader r = new WireReader(new byte[] {0x02, (byte) 0xC3, (byte) 0xA4});
      assertEquals("ä", r.readString());
    }

    @Test
    void truncatedLengthDelimitedThrows() {
      // Claims length=5 but only 2 bytes follow
      WireReader r = new WireReader(new byte[] {0x05, 0x41, 0x42});
      assertThrows(EOFException.class, r::readBytes);
    }

    @Test
    void negativeLengthThrows() {
      // Varint that decodes to a negative int32 (high bit set in 5th byte)
      // 0x80 0x80 0x80 0x80 0x08 = 2147483648 which is -2147483648 as int32
      WireReader r =
          new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08});
      IOException ex = assertThrows(IOException.class, r::readBytes);
      assertTrue(ex.getMessage().contains("Negative length"));
    }

    @Test
    void readMessage() throws IOException {
      // Embedded message: length=2, containing varint 150 (0x96 0x01)
      WireReader r = new WireReader(new byte[] {0x02, (byte) 0x96, 0x01});
      WireReader sub = r.readMessage();
      assertEquals(150, sub.readVarInt());
      assertFalse(sub.hasRemaining());
      assertFalse(r.hasRemaining());
    }
  }

  // --- Tags ---

  @Nested
  class Tags {

    @Test
    void fieldNumberAndWireType() {
      // Field 1, wire type 0 (varint) = (1 << 3) | 0 = 0x08
      assertEquals(1, WireReader.getFieldNumber(0x08));
      assertEquals(0, WireReader.getWireType(0x08));
    }

    @Test
    void fieldNumberAndWireTypeLengthDelimited() {
      // Field 2, wire type 2 = (2 << 3) | 2 = 0x12
      assertEquals(2, WireReader.getFieldNumber(0x12));
      assertEquals(2, WireReader.getWireType(0x12));
    }

    @Test
    void highFieldNumber() {
      // Field 100, wire type 0 = (100 << 3) | 0 = 800 = 0x320
      assertEquals(100, WireReader.getFieldNumber(0x320));
      assertEquals(0, WireReader.getWireType(0x320));
    }

    @Test
    void readTagAtEnd() throws IOException {
      WireReader r = new WireReader(new byte[] {});
      assertEquals(0, r.readTag());
    }

    @Test
    void readTagReturnsFullTag() throws IOException {
      // Field 1, varint = 0x08
      WireReader r = new WireReader(new byte[] {0x08, 0x00});
      int tag = r.readTag();
      assertEquals(1, WireReader.getFieldNumber(tag));
      assertEquals(0, WireReader.getWireType(tag));
    }
  }

  // --- Skip ---

  @Nested
  class Skip {

    @Test
    void skipVarint() throws IOException {
      // varint 300 (2 bytes) then varint 1
      WireReader r = new WireReader(new byte[] {(byte) 0xAC, 0x02, 0x01});
      r.skipField(WireReader.WIRETYPE_VARINT);
      assertEquals(1, r.readVarInt());
    }

    @Test
    void skipFixed64() throws IOException {
      WireReader r = new WireReader(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 0x42});
      r.skipField(WireReader.WIRETYPE_FIXED64);
      assertEquals(0x42, r.readVarInt());
    }

    @Test
    void skipFixed32() throws IOException {
      WireReader r = new WireReader(new byte[] {1, 2, 3, 4, 0x42});
      r.skipField(WireReader.WIRETYPE_FIXED32);
      assertEquals(0x42, r.readVarInt());
    }

    @Test
    void skipLengthDelimited() throws IOException {
      // length=3, 3 bytes of data, then varint 0x42
      WireReader r = new WireReader(new byte[] {0x03, 0x41, 0x42, 0x43, 0x42});
      r.skipField(WireReader.WIRETYPE_LENGTH_DELIMITED);
      assertEquals(0x42, r.readVarInt());
    }

    @Test
    void skipUnknownWireTypeThrows() {
      WireReader r = new WireReader(new byte[] {0x00});
      IOException ex = assertThrows(IOException.class, () -> r.skipField(3));
      assertTrue(ex.getMessage().contains("Unknown wire type"));
    }

    @Test
    void skipTruncatedFixed64Throws() {
      WireReader r = new WireReader(new byte[] {1, 2, 3});
      assertThrows(EOFException.class, () -> r.skipField(WireReader.WIRETYPE_FIXED64));
    }

    @Test
    void skipTruncatedFixed32Throws() {
      WireReader r = new WireReader(new byte[] {1, 2});
      assertThrows(EOFException.class, () -> r.skipField(WireReader.WIRETYPE_FIXED32));
    }

    @Test
    void skipTruncatedLengthDelimitedThrows() {
      // claims length=10, only 2 bytes
      WireReader r = new WireReader(new byte[] {0x0A, 0x01, 0x02});
      assertThrows(EOFException.class, () -> r.skipField(WireReader.WIRETYPE_LENGTH_DELIMITED));
    }
  }

  // --- Wire type validation ---

  @Nested
  class WireTypeValidation {

    @Test
    void expectWireTypeMatchDoesNotThrow() throws IOException {
      WireReader.expectWireType(1, WireReader.WIRETYPE_VARINT, WireReader.WIRETYPE_VARINT);
    }

    @Test
    void expectWireTypeMismatchThrows() {
      IOException ex =
          assertThrows(
              IOException.class,
              () ->
                  WireReader.expectWireType(
                      5, WireReader.WIRETYPE_VARINT, WireReader.WIRETYPE_LENGTH_DELIMITED));
      assertTrue(ex.getMessage().contains("Field 5"));
      assertTrue(ex.getMessage().contains("varint"));
      assertTrue(ex.getMessage().contains("length-delimited"));
    }
  }

  // --- Offset/limit constructor ---

  @Nested
  class OffsetLimit {

    @Test
    void readsOnlyWithinBounds() throws IOException {
      byte[] data = new byte[] {0x00, 0x01, 0x02, 0x03, 0x00};
      WireReader r = new WireReader(data, 1, 3);
      assertEquals(3, r.remaining());
      assertEquals(1, r.readVarInt());
      assertEquals(2, r.readVarInt());
      assertEquals(3, r.readVarInt());
      assertFalse(r.hasRemaining());
    }
  }

  // --- fromInputStream ---

  @Nested
  class FromInputStream {

    @Test
    void readsAllBytes() throws IOException {
      byte[] data = new byte[] {(byte) 0xAC, 0x02};
      WireReader r = WireReader.fromInputStream(new ByteArrayInputStream(data));
      assertEquals(300, r.readVarInt());
    }

    @Test
    void emptyStream() throws IOException {
      WireReader r = WireReader.fromInputStream(new ByteArrayInputStream(new byte[] {}));
      assertFalse(r.hasRemaining());
      assertEquals(0, r.readTag());
    }
  }
}
