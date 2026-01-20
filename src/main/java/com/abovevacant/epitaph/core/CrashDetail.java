package com.abovevacant.epitaph.core;

/** Additional crash detail information. */
public final class CrashDetail {

  /** Name of the crash detail. */
  public final byte[] name;

  /** Data associated with the crash detail. */
  public final byte[] data;

  public CrashDetail(final byte[] name, final byte[] data) {
    this.name = name;
    this.data = data;
  }
}
