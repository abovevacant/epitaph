package com.abovevacant.epitaph.core;

/** A memory-mapped region (typically an ELF binary). */
public final class MemoryMapping {

  /** Start address of the mapping. */
  public final long beginAddress;

  /** End address of the mapping. */
  public final long endAddress;

  /** Offset within the file. */
  public final long offset;

  /** Whether this mapping is readable. */
  public final boolean read;

  /** Whether this mapping is writable. */
  public final boolean write;

  /** Whether this mapping is executable. */
  public final boolean execute;

  /** Name of the mapped file (e.g., "/system/lib64/libc.so"). */
  public final String mappingName;

  /** Build ID of the ELF binary (hex string). */
  public final String buildId;

  /** Load bias of the ELF binary. */
  public final long loadBias;

  public MemoryMapping(
      final long beginAddress,
      final long endAddress,
      final long offset,
      final boolean read,
      final boolean write,
      final boolean execute,
      final String mappingName,
      final String buildId,
      final long loadBias) {
    this.beginAddress = beginAddress;
    this.endAddress = endAddress;
    this.offset = offset;
    this.read = read;
    this.write = write;
    this.execute = execute;
    this.mappingName = mappingName;
    this.buildId = buildId;
    this.loadBias = loadBias;
  }
}
