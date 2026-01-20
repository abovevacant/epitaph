package com.abovevacant.epitaph.core;

/** A cause of the crash. */
public final class Cause {

  /** Human-readable description of the cause. */
  public final String humanReadable;

  /** Memory error details, if this cause is a memory error. */
  public final MemoryError memoryError;

  public Cause(final String humanReadable, final MemoryError memoryError) {
    this.humanReadable = humanReadable;
    this.memoryError = memoryError;
  }
}
