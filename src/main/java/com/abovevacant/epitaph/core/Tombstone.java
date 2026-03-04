package com.abovevacant.epitaph.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    this.commandLine = Collections.unmodifiableList(commandLine);
    this.processUptime = processUptime;
    this.signal = signal;
    this.abortMessage = abortMessage;
    this.crashDetails = Collections.unmodifiableList(crashDetails);
    this.causes = Collections.unmodifiableList(causes);
    this.threads = Collections.unmodifiableMap(threads);
    this.guestThreads = Collections.unmodifiableMap(guestThreads);
    this.memoryMappings = Collections.unmodifiableList(memoryMappings);
    this.logBuffers = Collections.unmodifiableList(logBuffers);
    this.openFds = Collections.unmodifiableList(openFds);
    this.pageSize = pageSize;
    this.hasBeen16kbMode = hasBeen16kbMode;
    this.stackHistoryBuffer = stackHistoryBuffer;
  }

  /** Returns true if signal information is present. */
  public boolean hasSignal() {
    return signal != null;
  }

  /** Builder for constructing Tombstone instances with sensible defaults. */
  public static final class Builder {
    private Architecture arch = Architecture.NONE;
    private Architecture guestArch = Architecture.NONE;
    private String buildFingerprint = "";
    private String revision = "";
    private String timestamp = "";
    private int pid;
    private int tid;
    private int uid;
    private String selinuxLabel = "";
    private List<String> commandLine = new ArrayList<>();
    private int processUptime;
    private Signal signal;
    private String abortMessage = "";
    private List<CrashDetail> crashDetails = new ArrayList<>();
    private List<Cause> causes = new ArrayList<>();
    private Map<Integer, TombstoneThread> threads = new HashMap<>();
    private Map<Integer, TombstoneThread> guestThreads = new HashMap<>();
    private List<MemoryMapping> memoryMappings = new ArrayList<>();
    private List<LogBuffer> logBuffers = new ArrayList<>();
    private List<FD> openFds = new ArrayList<>();
    private int pageSize;
    private boolean hasBeen16kbMode;
    private StackHistoryBuffer stackHistoryBuffer;

    public Builder pid(int pid) {
      this.pid = pid;
      return this;
    }

    public Builder tid(int tid) {
      this.tid = tid;
      return this;
    }

    public Builder uid(int uid) {
      this.uid = uid;
      return this;
    }

    public Builder arch(Architecture arch) {
      this.arch = arch;
      return this;
    }

    public Builder guestArch(Architecture guestArch) {
      this.guestArch = guestArch;
      return this;
    }

    public Builder buildFingerprint(String buildFingerprint) {
      this.buildFingerprint = buildFingerprint;
      return this;
    }

    public Builder revision(String revision) {
      this.revision = revision;
      return this;
    }

    public Builder timestamp(String timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder selinuxLabel(String selinuxLabel) {
      this.selinuxLabel = selinuxLabel;
      return this;
    }

    public Builder commandLine(List<String> commandLine) {
      this.commandLine = commandLine;
      return this;
    }

    public Builder processUptime(int processUptime) {
      this.processUptime = processUptime;
      return this;
    }

    public Builder signal(Signal signal) {
      this.signal = signal;
      return this;
    }

    public Builder abortMessage(String abortMessage) {
      this.abortMessage = abortMessage;
      return this;
    }

    public Builder crashDetails(List<CrashDetail> crashDetails) {
      this.crashDetails = crashDetails;
      return this;
    }

    public Builder causes(List<Cause> causes) {
      this.causes = causes;
      return this;
    }

    public Builder threads(Map<Integer, TombstoneThread> threads) {
      this.threads = threads;
      return this;
    }

    public Builder guestThreads(Map<Integer, TombstoneThread> guestThreads) {
      this.guestThreads = guestThreads;
      return this;
    }

    public Builder memoryMappings(List<MemoryMapping> memoryMappings) {
      this.memoryMappings = memoryMappings;
      return this;
    }

    public Builder logBuffers(List<LogBuffer> logBuffers) {
      this.logBuffers = logBuffers;
      return this;
    }

    public Builder openFds(List<FD> openFds) {
      this.openFds = openFds;
      return this;
    }

    public Builder pageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    public Builder hasBeen16kbMode(boolean hasBeen16kbMode) {
      this.hasBeen16kbMode = hasBeen16kbMode;
      return this;
    }

    public Builder stackHistoryBuffer(StackHistoryBuffer stackHistoryBuffer) {
      this.stackHistoryBuffer = stackHistoryBuffer;
      return this;
    }

    /** Adds a thread, keyed by its id. */
    public Builder addThread(TombstoneThread thread) {
      this.threads.put(thread.id, thread);
      return this;
    }

    /** Adds a memory mapping. */
    public Builder addMemoryMapping(MemoryMapping mapping) {
      this.memoryMappings.add(mapping);
      return this;
    }

    public Tombstone build() {
      return new Tombstone(
          arch,
          guestArch,
          buildFingerprint,
          revision,
          timestamp,
          pid,
          tid,
          uid,
          selinuxLabel,
          new ArrayList<>(commandLine),
          processUptime,
          signal,
          abortMessage,
          new ArrayList<>(crashDetails),
          new ArrayList<>(causes),
          new HashMap<>(threads),
          new HashMap<>(guestThreads),
          new ArrayList<>(memoryMappings),
          new ArrayList<>(logBuffers),
          new ArrayList<>(openFds),
          pageSize,
          hasBeen16kbMode,
          stackHistoryBuffer);
    }
  }
}
