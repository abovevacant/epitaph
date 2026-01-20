package com.abovevacant.epitaph.core;

/** Signal information from a native crash. */
public final class Signal {

  /** Signal number (e.g., 11 for SIGSEGV). */
  public final int number;

  /** Signal name (e.g., "SIGSEGV"). */
  public final String name;

  /** Signal code (e.g., 1 for SEGV_MAPERR). */
  public final int code;

  /** Signal code name (e.g., "SEGV_MAPERR"). */
  public final String codeName;

  /** Whether sender information is available. */
  public final boolean hasSender;

  /** UID of the process that sent the signal. */
  public final int senderUid;

  /** PID of the process that sent the signal. */
  public final int senderPid;

  /** Whether fault address is available. */
  public final boolean hasFaultAddress;

  /** Fault address that caused the signal. */
  public final long faultAddress;

  /** Memory dump of the area adjacent to the fault, if available. */
  public final MemoryDump faultAdjacentMetadata;

  public Signal(
      final int number,
      final String name,
      final int code,
      final String codeName,
      final boolean hasSender,
      final int senderUid,
      final int senderPid,
      final boolean hasFaultAddress,
      final long faultAddress,
      final MemoryDump faultAdjacentMetadata) {
    this.number = number;
    this.name = name;
    this.code = code;
    this.codeName = codeName;
    this.hasSender = hasSender;
    this.senderUid = senderUid;
    this.senderPid = senderPid;
    this.hasFaultAddress = hasFaultAddress;
    this.faultAddress = faultAddress;
    this.faultAdjacentMetadata = faultAdjacentMetadata;
  }
}
