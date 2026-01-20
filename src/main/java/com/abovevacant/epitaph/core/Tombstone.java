package com.abovevacant.epitaph.core;

import java.util.List;
import java.util.Map;

/** Parsed Android tombstone (native crash dump). */
public final class Tombstone {

  /** CPU architecture of the crashed process. */
  public final Architecture arch;

  /** Guest CPU architecture (for emulated processes). */
  public final Architecture guestArch;

  /** Android build fingerprint. */
  public final String buildFingerprint;

  /** Device revision. */
  public final String revision;

  /** Timestamp of the crash. */
  public final String timestamp;

  /** Process ID. */
  public final int pid;

  /** Thread ID that crashed. */
  public final int tid;

  /** User ID. */
  public final int uid;

  /** SELinux label. */
  public final String selinuxLabel;

  /** Command line arguments. */
  public final List<String> commandLine;

  /** Process uptime in seconds. */
  public final int processUptime;

  /** Signal information, or null if not present. */
  public final Signal signal;

  /** Abort message, or empty string if not present. */
  public final String abortMessage;

  /** Additional crash details. */
  public final List<CrashDetail> crashDetails;

  /** Causes of the crash. */
  public final List<Cause> causes;

  /** All threads in the process, keyed by thread ID. */
  public final Map<Integer, TombstoneThread> threads;

  /** Guest threads (for emulated processes), keyed by thread ID. */
  public final Map<Integer, TombstoneThread> guestThreads;

  /** Memory mappings (loaded libraries/executables). */
  public final List<MemoryMapping> memoryMappings;

  /** Log buffers (logcat dumps). */
  public final List<LogBuffer> logBuffers;

  /** Open file descriptors. */
  public final List<FD> openFds;

  /** Page size in bytes. */
  public final int pageSize;

  /** Whether the process has been in 16KB page mode. */
  public final boolean hasBeen16kbMode;

  /** Stack history buffer for shadow call stack. */
  public final StackHistoryBuffer stackHistoryBuffer;

  public Tombstone(
      final Architecture arch,
      final Architecture guestArch,
      final String buildFingerprint,
      final String revision,
      final String timestamp,
      final int pid,
      final int tid,
      final int uid,
      final String selinuxLabel,
      final List<String> commandLine,
      final int processUptime,
      final Signal signal,
      final String abortMessage,
      final List<CrashDetail> crashDetails,
      final List<Cause> causes,
      final Map<Integer, TombstoneThread> threads,
      final Map<Integer, TombstoneThread> guestThreads,
      final List<MemoryMapping> memoryMappings,
      final List<LogBuffer> logBuffers,
      final List<FD> openFds,
      final int pageSize,
      final boolean hasBeen16kbMode,
      final StackHistoryBuffer stackHistoryBuffer) {
    this.arch = arch;
    this.guestArch = guestArch;
    this.buildFingerprint = buildFingerprint;
    this.revision = revision;
    this.timestamp = timestamp;
    this.pid = pid;
    this.tid = tid;
    this.uid = uid;
    this.selinuxLabel = selinuxLabel;
    this.commandLine = commandLine;
    this.processUptime = processUptime;
    this.signal = signal;
    this.abortMessage = abortMessage;
    this.crashDetails = crashDetails;
    this.causes = causes;
    this.threads = threads;
    this.guestThreads = guestThreads;
    this.memoryMappings = memoryMappings;
    this.logBuffers = logBuffers;
    this.openFds = openFds;
    this.pageSize = pageSize;
    this.hasBeen16kbMode = hasBeen16kbMode;
    this.stackHistoryBuffer = stackHistoryBuffer;
  }

  /** Returns true if signal information is present. */
  public boolean hasSignal() {
    return signal != null;
  }
}
