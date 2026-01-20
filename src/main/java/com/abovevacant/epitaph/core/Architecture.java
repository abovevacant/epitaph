package com.abovevacant.epitaph.core;

/** CPU architecture of the crashed process. */
public enum Architecture {
  ARM32(0),
  ARM64(1),
  X86(2),
  X86_64(3),
  RISCV64(4),
  NONE(5);

  private final int value;

  Architecture(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  public static Architecture fromValue(int value) {
    for (Architecture arch : values()) {
      if (arch.value == value) {
        return arch;
      }
    }
    return NONE;
  }
}
