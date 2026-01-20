package com.abovevacant.epitaph.core;

/** A single log message from logcat. */
public final class LogMessage {

  /** Timestamp of the log message. */
  public final String timestamp;

  /** Process ID that generated the log. */
  public final int pid;

  /** Thread ID that generated the log. */
  public final int tid;

  /** Log priority level. */
  public final int priority;

  /** Log tag. */
  public final String tag;

  /** Log message content. */
  public final String message;

  public LogMessage(
      final String timestamp,
      final int pid,
      final int tid,
      final int priority,
      final String tag,
      final String message) {
    this.timestamp = timestamp;
    this.pid = pid;
    this.tid = tid;
    this.priority = priority;
    this.tag = tag;
    this.message = message;
  }
}
