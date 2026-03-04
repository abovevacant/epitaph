package com.abovevacant.epitaph.core;

import java.util.Collections;
import java.util.List;

/** A thread from the crashed process. */
public final class TombstoneThread {

  /** Thread ID. */
  public final int id;

  /** Thread name, or empty string if unknown. */
  public final String name;

  /** CPU registers at time of crash. */
  public final List<Register> registers;

  /** Notes about the backtrace (e.g., warnings about unwinding). */
  public final List<String> backtraceNote;

  /** List of ELF files that couldn't be read. */
  public final List<String> unreadableElfFiles;

  /** Backtrace frames (most recent call first). */
  public final List<BacktraceFrame> backtrace;

  /** Memory dumps associated with this thread. */
  public final List<MemoryDump> memoryDump;

  /** Tagged address control register value. */
  public final long taggedAddrCtrl;

  /** PAC enabled keys value. */
  public final long pacEnabledKeys;

  public TombstoneThread(
      final int id,
      final String name,
      final List<Register> registers,
      final List<String> backtraceNote,
      final List<String> unreadableElfFiles,
      final List<BacktraceFrame> backtrace,
      final List<MemoryDump> memoryDump,
      final long taggedAddrCtrl,
      final long pacEnabledKeys) {
    this.id = id;
    this.name = name;
    this.registers = Collections.unmodifiableList(registers);
    this.backtraceNote = Collections.unmodifiableList(backtraceNote);
    this.unreadableElfFiles = Collections.unmodifiableList(unreadableElfFiles);
    this.backtrace = Collections.unmodifiableList(backtrace);
    this.memoryDump = Collections.unmodifiableList(memoryDump);
    this.taggedAddrCtrl = taggedAddrCtrl;
    this.pacEnabledKeys = pacEnabledKeys;
  }
}
