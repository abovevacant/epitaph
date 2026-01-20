package com.abovevacant.epitaph.core;

/** A CPU register name and value. */
public final class Register {

  /** Register name (e.g., "x0", "pc", "sp"). */
  public final String name;

  /** Register value as unsigned 64-bit integer. */
  public final long value;

  public Register(final String name, final long value) {
    this.name = name;
    this.value = value;
  }
}
