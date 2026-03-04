package com.abovevacant.epitaph.core;

import java.util.Collections;
import java.util.List;

/** Information about a heap allocation. */
public final class HeapObject {

  /** Address of the heap object. */
  public final long address;

  /** Size of the allocation. */
  public final long size;

  /** Thread ID that allocated the object. */
  public final long allocationTid;

  /** Backtrace at allocation time. */
  public final List<BacktraceFrame> allocationBacktrace;

  /** Thread ID that deallocated the object. */
  public final long deallocationTid;

  /** Backtrace at deallocation time. */
  public final List<BacktraceFrame> deallocationBacktrace;

  public HeapObject(
      final long address,
      final long size,
      final long allocationTid,
      final List<BacktraceFrame> allocationBacktrace,
      final long deallocationTid,
      final List<BacktraceFrame> deallocationBacktrace) {
    this.address = address;
    this.size = size;
    this.allocationTid = allocationTid;
    this.allocationBacktrace = Collections.unmodifiableList(allocationBacktrace);
    this.deallocationTid = deallocationTid;
    this.deallocationBacktrace = Collections.unmodifiableList(deallocationBacktrace);
  }
}
