package com.abovevacant.epitaph.core;

/** An open file descriptor. */
public final class FD {

  /** File descriptor number. */
  public final int fd;

  /** Path of the file. */
  public final String path;

  /** Owner of the file descriptor. */
  public final String owner;

  /** Tag associated with the file descriptor. */
  public final long tag;

  /** Additional descriptor information, or empty string if absent (added in Android 17). */
  public final String details;

  public FD(final int fd, final String path, final String owner, final long tag) {
    this(fd, path, owner, tag, "");
  }

  /** Constructs a descriptor including Android 17's additional details. */
  public FD(
      final int fd, final String path, final String owner, final long tag, final String details) {
    this.fd = fd;
    this.path = path;
    this.owner = owner;
    this.tag = tag;
    this.details = details;
  }
}
