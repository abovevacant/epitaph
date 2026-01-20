package com.abovevacant.epitaph.core;

/** ARM Memory Tagging Extension metadata. */
public final class ArmMTEMetadata {

  /** One memory tag per granule (e.g. every 16 bytes) of regular memory. */
  public final byte[] memoryTags;

  public ArmMTEMetadata(final byte[] memoryTags) {
    this.memoryTags = memoryTags;
  }
}
