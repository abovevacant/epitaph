package com.abovevacant.epitaph.core;

/** A dump of memory contents. */
public final class MemoryDump {

  /** Name of the register this dump is associated with. */
  public final String registerName;

  /** Name of the memory mapping. */
  public final String mappingName;

  /** Beginning address of the dump. */
  public final long beginAddress;

  /** Raw memory contents. */
  public final byte[] memory;

  /** ARM MTE metadata, if present. */
  public final ArmMTEMetadata armMteMetadata;

  public MemoryDump(
      final String registerName,
      final String mappingName,
      final long beginAddress,
      final byte[] memory,
      final ArmMTEMetadata armMteMetadata) {
    this.registerName = registerName;
    this.mappingName = mappingName;
    this.beginAddress = beginAddress;
    this.memory = memory;
    this.armMteMetadata = armMteMetadata;
  }
}
