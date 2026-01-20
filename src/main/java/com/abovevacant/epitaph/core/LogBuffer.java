package com.abovevacant.epitaph.core;

import java.util.List;

/** A buffer of log messages. */
public final class LogBuffer {

  /** Name of the log buffer (e.g., "main", "system"). */
  public final String name;

  /** Log messages in this buffer. */
  public final List<LogMessage> logs;

  public LogBuffer(final String name, final List<LogMessage> logs) {
    this.name = name;
    this.logs = logs;
  }
}
