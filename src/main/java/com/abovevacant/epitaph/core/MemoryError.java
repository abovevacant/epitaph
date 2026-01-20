package com.abovevacant.epitaph.core;

/** Information about a memory error. */
public final class MemoryError {

  /** Tool that detected the memory error. */
  public enum Tool {
    GWP_ASAN(0),
    SCUDO(1);

    private final int value;

    Tool(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public static Tool fromValue(int value) {
      for (Tool tool : values()) {
        if (tool.value == value) {
          return tool;
        }
      }
      return GWP_ASAN;
    }
  }

  /** Type of memory error. */
  public enum Type {
    UNKNOWN(0),
    USE_AFTER_FREE(1),
    DOUBLE_FREE(2),
    INVALID_FREE(3),
    BUFFER_OVERFLOW(4),
    BUFFER_UNDERFLOW(5);

    private final int value;

    Type(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public static Type fromValue(int value) {
      for (Type type : values()) {
        if (type.value == value) {
          return type;
        }
      }
      return UNKNOWN;
    }
  }

  /** Tool that detected the error. */
  public final Tool tool;

  /** Type of memory error. */
  public final Type type;

  /** Heap object involved in the error, if applicable. */
  public final HeapObject heap;

  public MemoryError(final Tool tool, final Type type, final HeapObject heap) {
    this.tool = tool;
    this.type = type;
    this.heap = heap;
  }
}
