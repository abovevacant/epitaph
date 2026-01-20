package com.abovevacant.epitaph.core;

/** A single frame in a backtrace. */
public final class BacktraceFrame {

  /** Relative program counter (offset within the binary). */
  public final long relPc;

  /** Absolute program counter (instruction address). */
  public final long pc;

  /** Stack pointer. */
  public final long sp;

  /** Function name, or empty string if unknown. */
  public final String functionName;

  /** Offset within the function. */
  public final long functionOffset;

  /** File/library name, or empty string if unknown. */
  public final String fileName;

  /** Offset within the file mapping. */
  public final long fileMapOffset;

  /** Build ID of the binary (hex string). */
  public final String buildId;

  public BacktraceFrame(
      final long relPc,
      final long pc,
      final long sp,
      final String functionName,
      final long functionOffset,
      final String fileName,
      final long fileMapOffset,
      final String buildId) {
    this.relPc = relPc;
    this.pc = pc;
    this.sp = sp;
    this.functionName = functionName;
    this.functionOffset = functionOffset;
    this.fileName = fileName;
    this.fileMapOffset = fileMapOffset;
    this.buildId = buildId;
  }
}
