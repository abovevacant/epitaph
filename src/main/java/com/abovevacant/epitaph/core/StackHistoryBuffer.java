package com.abovevacant.epitaph.core;

import java.util.Collections;
import java.util.List;

/** Stack history buffer for shadow call stack. */
public final class StackHistoryBuffer {

  /** Thread ID. */
  public final long tid;

  /** Entries in the stack history buffer. */
  public final List<StackHistoryBufferEntry> entries;

  public StackHistoryBuffer(final long tid, final List<StackHistoryBufferEntry> entries) {
    this.tid = tid;
    this.entries = Collections.unmodifiableList(entries);
  }
}
