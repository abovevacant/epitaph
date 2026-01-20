package com.abovevacant.epitaph.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Low-level protobuf wire format reader.
 *
 * <p>Supports reading:
 *
 * <ul>
 *   <li>Wire type 0: varint (int32, int64, uint32, uint64, bool, enum)
 *   <li>Wire type 1: 64-bit fixed
 *   <li>Wire type 2: Length-delimited (string, bytes, embedded messages)
 *   <li>Wire type 5: 32-bit fixed
 * </ul>
 */
public final class WireReader {

  public static final int WIRETYPE_VARINT = 0;
  public static final int WIRETYPE_FIXED64 = 1;
  public static final int WIRETYPE_LENGTH_DELIMITED = 2;
  public static final int WIRETYPE_FIXED32 = 5;

  private final byte[] buffer;
  private int position;
  private final int limit;

  public WireReader(final byte[] data) {
    this(data, 0, data.length);
  }

  public WireReader(final byte[] data, final int offset, final int length) {
    this.buffer = data;
    this.position = offset;
    this.limit = offset + length;
  }

  /** Creates a WireReader from an InputStream by reading all bytes. */
  public static WireReader fromInputStream(final InputStream stream) throws IOException {
    // Read all bytes from the stream
    final java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
    final byte[] buf = new byte[8192];
    int bytesRead;
    while ((bytesRead = stream.read(buf)) != -1) {
      os.write(buf, 0, bytesRead);
    }
    return new WireReader(os.toByteArray());
  }

  public boolean hasRemaining() {
    return position < limit;
  }

  public int remaining() {
    return limit - position;
  }

  /**
   * Reads a tag (field number + wire type).
   *
   * @return the tag value, or 0 if at end of data
   */
  public int readTag() throws IOException {
    if (!hasRemaining()) {
      return 0;
    }
    return (int) readVarInt();
  }

  /** Extracts the field number from a tag. */
  public static int getFieldNumber(final int tag) {
    return tag >>> 3;
  }

  /** Extracts the wire type from a tag. */
  public static int getWireType(final int tag) {
    return tag & 0x7;
  }

  /**
   * Reads a varint (variable-length integer).
   *
   * <p>varints use 7 bits per byte, with the MSB indicating continuation.
   */
  public long readVarInt() throws IOException {
    long result = 0;
    int shift = 0;
    while (shift < 64) {
      if (!hasRemaining()) {
        throw new EOFException("Truncated varint");
      }
      final byte b = buffer[position++];
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
    throw new IOException("Malformed varint");
  }

  /** Reads a 32-bit varint. */
  public int readVarInt32() throws IOException {
    return (int) readVarInt();
  }

  /** Reads a 64-bit fixed value (little-endian). */
  public long readFixed64() throws IOException {
    if (remaining() < 8) {
      throw new EOFException("Not enough bytes for fixed64");
    }
    long result = 0;
    for (int i = 0; i < 8; i++) {
      result |= (long) (buffer[position++] & 0xFF) << (i * 8);
    }
    return result;
  }

  /** Reads a 32-bit fixed value (little-endian). */
  public int readFixed32() throws IOException {
    if (remaining() < 4) {
      throw new EOFException("Not enough bytes for fixed32");
    }
    int result = 0;
    for (int i = 0; i < 4; i++) {
      result |= (buffer[position++] & 0xFF) << (i * 8);
    }
    return result;
  }

  /** Reads a length-delimited field as raw bytes. */
  public byte[] readBytes() throws IOException {
    final int length = readVarInt32();
    if (length < 0) {
      throw new IOException("Negative length: " + length);
    }
    if (remaining() < length) {
      throw new EOFException("Not enough bytes for length-delimited field");
    }
    final byte[] result = new byte[length];
    System.arraycopy(buffer, position, result, 0, length);
    position += length;
    return result;
  }

  /** Reads a length-delimited field as a UTF-8 string. */
  public String readString() throws IOException {
    return new String(readBytes(), StandardCharsets.UTF_8);
  }

  /** Reads a boolean (as varint). */
  public boolean readBool() throws IOException {
    return readVarInt() != 0;
  }

  /** Creates a sub-reader for an embedded message. */
  public WireReader readMessage() throws IOException {
    final byte[] data = readBytes();
    return new WireReader(data);
  }

  /** Skips a field based on its wire type. */
  public void skipField(final int wireType) throws IOException {
    switch (wireType) {
      case WIRETYPE_VARINT:
        readVarInt();
        break;
      case WIRETYPE_FIXED64:
        if (remaining() < 8) {
          throw new EOFException("Not enough bytes to skip fixed64");
        }
        position += 8;
        break;
      case WIRETYPE_LENGTH_DELIMITED:
        final int length = readVarInt32();
        if (remaining() < length) {
          throw new EOFException("Not enough bytes to skip length-delimited");
        }
        position += length;
        break;
      case WIRETYPE_FIXED32:
        if (remaining() < 4) {
          throw new EOFException("Not enough bytes to skip fixed32");
        }
        position += 4;
        break;
      default:
        throw new IOException("Unknown wire type: " + wireType);
    }
  }
}
