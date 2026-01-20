package com.abovevacant.epitaph.core;

/** An entry in the stack history buffer. */
public final class StackHistoryBufferEntry {

  /** Address information as a backtrace frame. */
  public final BacktraceFrame addr;

  /** Frame pointer. */
  public final long fp;

  /** Tag value. */
  public final long tag;

  public StackHistoryBufferEntry(final BacktraceFrame addr, final long fp, final long tag) {
    this.addr = addr;
    this.fp = fp;
    this.tag = tag;
  }
}
