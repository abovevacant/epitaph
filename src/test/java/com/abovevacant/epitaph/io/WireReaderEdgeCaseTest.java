package com.abovevacant.epitaph.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests that challenge WireReader behavior at boundaries and edge cases, rather than simply
 * confirming existing behavior.
 */
class WireReaderEdgeCaseTest {

  // --- Varint edge cases ---

  @Nested
  class VarintEdgeCases {

    @Test
    void largeVarintTruncatedToInt32() throws IOException {
      // 0x1_0000_0000 (4294967296) — doesn't fit in int32, readVarInt32 casts to int → 0.
      // This matches standard protobuf behavior: protobuf-java's readInt32() does the same
      // long-to-int cast. The int32/uint32 wire format is defined this way.
      WireReader r =
          new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10});
      long full = 0x1_0000_0000L;
      assertEquals(full, r.readVarInt());

      r = new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10});
      assertEquals(0, r.readVarInt32());
    }

    @Test
    void varintMaxPositiveInt32() throws IOException {
      // Integer.MAX_VALUE = 2147483647 = 0x7FFFFFFF
      WireReader r =
          new WireReader(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07});
      assertEquals(Integer.MAX_VALUE, r.readVarInt32());
    }

    @Test
    void varintMinNegativeInt32() throws IOException {
      // Integer.MIN_VALUE = -2147483648 as signed int32
      // As uint64 varint: 0xFFFFFFFF80000000 (sign-extended by protobuf for negative int32)
      // Actually, protobuf encodes negative int32 as 10-byte sign-extended varint
      WireReader r =
          new WireReader(
              new byte[] {
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0x80,
                (byte) 0xF8,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                0x01
              });
      assertEquals(Integer.MIN_VALUE, r.readVarInt32());
    }

    @Test
    void varintExactly10BytesMaxValid() throws IOException {
      // Maximum valid varint is 10 bytes with the last byte having only 1 bit
      // This represents 0xFFFFFFFFFFFFFFFF (-1 as signed)
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
      assertEquals(-1L, r.readVarInt());
    }

    @Test
    void varintWithPayloadBitsBeyond64IsRejected() {
      // A 10-byte varint may only use the lowest bit in the final byte. Anything larger
      // encodes a value that does not fit in uint64 and must not be silently truncated.
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
                0x02
              });
      IOException ex = assertThrows(IOException.class, r::readVarInt);
      assertTrue(ex.getMessage().contains("Malformed varint"));
    }

    @Test
    void varintWithUnnecessaryZeroPadding() throws IOException {
      // Value 1 encoded with unnecessary continuation bytes: 0x81 0x00
      // This is technically valid protobuf (overlong encoding)
      WireReader r = new WireReader(new byte[] {(byte) 0x81, 0x00});
      assertEquals(1, r.readVarInt());
    }

    @Test
    void varintAllContinuationBytesZeroPayload() throws IOException {
      // 0x80 0x80 0x00 = value 0 with overlong encoding
      WireReader r = new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, 0x00});
      assertEquals(0, r.readVarInt());
    }
  }

  // --- Tag parsing edge cases ---

  @Nested
  class TagEdgeCases {

    @Test
    void zeroTagIsRejected() {
      // A tag value of 0 means field number 0, which protobuf reserves as invalid.
      // EOF is represented by having no bytes left, not by an encoded zero tag.
      WireReader r = new WireReader(new byte[] {0x00});
      IOException ex = assertThrows(IOException.class, r::readTag);
      assertTrue(ex.getMessage().contains("field number 0"));
    }

    @Test
    void zeroTagMidStreamIsRejected() {
      // This used to silently terminate parsing and ignore the remaining valid field.
      WireReader r = new WireReader(new byte[] {0x00, 0x08, 0x01});
      IOException ex = assertThrows(IOException.class, r::readTag);
      assertTrue(ex.getMessage().contains("field number 0"));
      assertTrue(r.hasRemaining());
      assertEquals(2, r.remaining());
    }

    @Test
    void tagWithFieldNumber0() {
      // Tag = 0x00 means field 0, wire type 0. Field 0 is reserved.
      // getFieldNumber(0) == 0
      assertEquals(0, WireReader.getFieldNumber(0));
      assertEquals(0, WireReader.getWireType(0));
    }

    @Test
    void tagWithFieldNumber0IsRejectedWhenRead() {
      WireReader r = new WireReader(new byte[] {0x05}); // field 0, wire type fixed32
      IOException ex = assertThrows(IOException.class, r::readTag);
      assertTrue(ex.getMessage().contains("field number 0"));
    }

    @Test
    void oversizedTagIsRejected() {
      // Encode a tag with a field number above protobuf's maximum:
      // field 536870912 wire type 0 = 4294967296 = 0x1_0000_0000.
      // This used to truncate to 0 and stop parsing.
      WireReader r =
          new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10});
      IOException ex = assertThrows(IOException.class, r::readTag);
      assertTrue(ex.getMessage().contains("Invalid tag"));
    }

    @Test
    void negativeDecodedTagIsRejected() {
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
      IOException ex = assertThrows(IOException.class, r::readTag);
      assertTrue(ex.getMessage().contains("Invalid tag"));
    }

    @Test
    void highFieldNumberWithinInt32Range() throws IOException {
      // Field 536870911 (max that fits in 29 bits) wire type 0
      // = (536870911 << 3) | 0 = 4294967288 = 0xFFFFFFF8
      // As int this is negative but getFieldNumber uses >>> so it works
      byte[] encoded = new byte[] {(byte) 0xF8, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F};
      WireReader r = new WireReader(encoded);
      int tag = r.readTag();
      assertEquals(536870911, WireReader.getFieldNumber(tag));
      assertEquals(0, WireReader.getWireType(tag));
    }
  }

  // --- Length-delimited edge cases ---

  @Nested
  class LengthDelimitedEdgeCases {

    @Test
    void lengthExactlyEqualsRemaining() throws IOException {
      // length=3, exactly 3 bytes — should work
      WireReader r = new WireReader(new byte[] {0x03, 0x41, 0x42, 0x43});
      byte[] result = r.readBytes();
      assertEquals(3, result.length);
      assertFalse(r.hasRemaining());
    }

    @Test
    void lengthExceedsRemainingByOne() {
      // length=4, only 3 bytes
      WireReader r = new WireReader(new byte[] {0x04, 0x41, 0x42, 0x43});
      assertThrows(EOFException.class, r::readBytes);
    }

    @Test
    void zeroLengthMessageProducesEmptySubReader() throws IOException {
      // Embedded message with length 0
      WireReader r = new WireReader(new byte[] {0x00});
      WireReader sub = r.readMessage();
      assertFalse(sub.hasRemaining());
      assertEquals(0, sub.readTag()); // immediately returns end
    }

    @Test
    void lengthExceedingIntMaxIsRejected() {
      // Varint 2147483648 (0x80000000) must be rejected as too large for an in-memory Java
      // byte array, rather than cast to Integer.MIN_VALUE.
      WireReader r =
          new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08});
      IOException ex = assertThrows(IOException.class, r::readBytes);
      assertTrue(ex.getMessage().contains("Length too large"));
    }

    @Test
    void lengthAtUint32BoundaryDoesNotWrapToZero() {
      // 4294967296 used to cast to int zero, causing readBytes() to return an empty array and
      // leave the stream misaligned.
      WireReader r =
          new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10});
      IOException ex = assertThrows(IOException.class, r::readBytes);
      assertTrue(ex.getMessage().contains("Length too large"));
    }

    @Test
    void negativeDecodedLengthIsRejected() {
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
      IOException ex = assertThrows(IOException.class, r::readBytes);
      assertTrue(ex.getMessage().contains("Negative length"));
    }

    @Test
    void veryLargeLengthWithInsufficientData() {
      // length = 1000000, but only a few bytes available
      // 1000000 = 0xF4240 -> varint: 0xC0 0xC4 0x07 (I think... let me just use a known encoding)
      // Actually doesn't matter for the test — any large length that exceeds remaining
      WireReader r = new WireReader(new byte[] {(byte) 0xC0, (byte) 0xC4, 0x07, 0x01, 0x02});
      assertThrows(EOFException.class, r::readBytes);
    }
  }

  // --- Skip edge cases ---

  @Nested
  class SkipEdgeCases {

    @Test
    void skipVarintWithOverlongEncoding() throws IOException {
      // Skip a varint encoded as 0x80 0x80 0x00 (overlong zero), then read next value
      WireReader r = new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, 0x00, 0x42});
      r.skipField(WireReader.WIRETYPE_VARINT);
      assertEquals(0x42, r.readVarInt());
    }

    @Test
    void skipLengthDelimitedExactlyToEnd() throws IOException {
      // length=3, 3 bytes — skip consumes everything
      WireReader r = new WireReader(new byte[] {0x03, 0x41, 0x42, 0x43});
      r.skipField(WireReader.WIRETYPE_LENGTH_DELIMITED);
      assertFalse(r.hasRemaining());
    }

    @Test
    void skipLengthDelimitedRejectsLengthTooLarge() {
      WireReader r =
          new WireReader(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08});
      IOException ex =
          assertThrows(IOException.class, () -> r.skipField(WireReader.WIRETYPE_LENGTH_DELIMITED));
      assertTrue(ex.getMessage().contains("Length too large"));
    }

    @Test
    void skipWireType3Throws() {
      // Wire type 3 = start group (deprecated)
      WireReader r = new WireReader(new byte[] {0x00});
      IOException ex = assertThrows(IOException.class, () -> r.skipField(3));
      assertTrue(ex.getMessage().contains("Unknown wire type: 3"));
    }

    @Test
    void skipWireType4Throws() {
      // Wire type 4 = end group (deprecated)
      WireReader r = new WireReader(new byte[] {0x00});
      IOException ex = assertThrows(IOException.class, () -> r.skipField(4));
      assertTrue(ex.getMessage().contains("Unknown wire type: 4"));
    }

    @Test
    void skipWireType6Throws() {
      WireReader r = new WireReader(new byte[] {0x00});
      IOException ex = assertThrows(IOException.class, () -> r.skipField(6));
      assertTrue(ex.getMessage().contains("Unknown wire type: 6"));
    }

    @Test
    void skipWireType7Throws() {
      WireReader r = new WireReader(new byte[] {0x00});
      IOException ex = assertThrows(IOException.class, () -> r.skipField(7));
      assertTrue(ex.getMessage().contains("Unknown wire type: 7"));
    }
  }

  // --- Sub-reader isolation ---

  @Nested
  class SubReaderIsolation {

    @Test
    void subReaderDoesNotConsumeParentBytes() throws IOException {
      // Parent: [length=2, 0x08, 0x01, 0x42]
      // Sub-reader should read only [0x08, 0x01], parent should still have 0x42
      WireReader parent = new WireReader(new byte[] {0x02, 0x08, 0x01, 0x42});
      WireReader sub = parent.readMessage();
      assertEquals(2, sub.remaining());
      assertEquals(0x08, sub.readVarInt()); // consume sub-reader
      assertEquals(0x01, sub.readVarInt());
      assertFalse(sub.hasRemaining());
      assertTrue(parent.hasRemaining());
      assertEquals(0x42, parent.readVarInt()); // parent's remaining byte
    }

    @Test
    void subReaderCannotReadBeyondItsLimit() throws IOException {
      // Sub-reader has 1 byte, tries to read 2-byte varint
      WireReader parent = new WireReader(new byte[] {0x01, (byte) 0x80, 0x42});
      WireReader sub = parent.readMessage();
      assertEquals(1, sub.remaining());
      // 0x80 has continuation bit — sub-reader should hit EOF since it only has 1 byte
      assertThrows(EOFException.class, sub::readVarInt);
    }
  }

  // --- expectWireType edge cases ---

  @Nested
  class ExpectWireTypeEdgeCases {

    @Test
    void errorMessageIncludesUnknownForBogusWireTypes() {
      IOException ex = assertThrows(IOException.class, () -> WireReader.expectWireType(1, 0, 6));
      assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void errorMessageIncludesFixed64() {
      IOException ex = assertThrows(IOException.class, () -> WireReader.expectWireType(1, 1, 0));
      assertTrue(ex.getMessage().contains("fixed64"));
    }

    @Test
    void errorMessageIncludesFixed32() {
      IOException ex = assertThrows(IOException.class, () -> WireReader.expectWireType(1, 5, 0));
      assertTrue(ex.getMessage().contains("fixed32"));
    }
  }

  // --- Offset/limit boundary ---

  @Nested
  class OffsetLimitBoundary {

    @Test
    void offsetAtArrayEndIsAllowed() {
      byte[] data = new byte[] {0x01, 0x02};
      WireReader r = new WireReader(data, 2, 0);
      assertFalse(r.hasRemaining());
      assertEquals(0, r.remaining());
    }

    @Test
    void constructorRejectsNegativeOffset() {
      byte[] data = new byte[] {0x01, 0x02};
      assertThrows(IndexOutOfBoundsException.class, () -> new WireReader(data, -1, 0));
    }

    @Test
    void constructorRejectsNegativeLength() {
      byte[] data = new byte[] {0x01, 0x02};
      assertThrows(IndexOutOfBoundsException.class, () -> new WireReader(data, 0, -1));
    }

    @Test
    void constructorRejectsOffsetPastArrayEnd() {
      byte[] data = new byte[] {0x01, 0x02};
      assertThrows(IndexOutOfBoundsException.class, () -> new WireReader(data, 3, 0));
    }

    @Test
    void constructorRejectsLengthPastArrayEnd() {
      byte[] data = new byte[] {0x01, 0x02};
      assertThrows(IndexOutOfBoundsException.class, () -> new WireReader(data, 1, 2));
    }

    @Test
    void zeroLengthReader() throws IOException {
      WireReader r = new WireReader(new byte[] {0x42}, 0, 0);
      assertFalse(r.hasRemaining());
      assertEquals(0, r.readTag());
    }
  }
}
