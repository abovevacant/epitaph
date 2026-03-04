package com.abovevacant.epitaph.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TombstoneBuilderTest {

  @Nested
  class Defaults {

    @Test
    void emptyBuilderProducesDefaults() {
      Tombstone t = new Tombstone.Builder().build();

      assertEquals(Architecture.NONE, t.arch);
      assertEquals(Architecture.NONE, t.guestArch);
      assertEquals("", t.buildFingerprint);
      assertEquals("", t.revision);
      assertEquals("", t.timestamp);
      assertEquals(0, t.pid);
      assertEquals(0, t.tid);
      assertEquals(0, t.uid);
      assertEquals("", t.selinuxLabel);
      assertTrue(t.commandLine.isEmpty());
      assertEquals(0, t.processUptime);
      assertNull(t.signal);
      assertEquals("", t.abortMessage);
      assertTrue(t.crashDetails.isEmpty());
      assertTrue(t.causes.isEmpty());
      assertTrue(t.threads.isEmpty());
      assertTrue(t.guestThreads.isEmpty());
      assertTrue(t.memoryMappings.isEmpty());
      assertTrue(t.logBuffers.isEmpty());
      assertTrue(t.openFds.isEmpty());
      assertEquals(0, t.pageSize);
      assertFalse(t.hasBeen16kbMode);
      assertNull(t.stackHistoryBuffer);
    }
  }

  @Nested
  class ScalarSetters {

    @Test
    void allScalarsRoundTrip() {
      Tombstone t =
          new Tombstone.Builder()
              .arch(Architecture.ARM64)
              .guestArch(Architecture.X86)
              .buildFingerprint("google/device/1.0")
              .revision("rev2")
              .timestamp("2025-06-01T00:00:00Z")
              .pid(1234)
              .tid(5678)
              .uid(10042)
              .selinuxLabel("u:r:untrusted_app:s0")
              .processUptime(300)
              .abortMessage("crashed hard")
              .pageSize(4096)
              .hasBeen16kbMode(true)
              .build();

      assertEquals(Architecture.ARM64, t.arch);
      assertEquals(Architecture.X86, t.guestArch);
      assertEquals("google/device/1.0", t.buildFingerprint);
      assertEquals("rev2", t.revision);
      assertEquals("2025-06-01T00:00:00Z", t.timestamp);
      assertEquals(1234, t.pid);
      assertEquals(5678, t.tid);
      assertEquals(10042, t.uid);
      assertEquals("u:r:untrusted_app:s0", t.selinuxLabel);
      assertEquals(300, t.processUptime);
      assertEquals("crashed hard", t.abortMessage);
      assertEquals(4096, t.pageSize);
      assertTrue(t.hasBeen16kbMode);
    }

    @Test
    void signalRoundTrip() {
      Signal sig = new Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, true, 0xDEADL, null);
      Tombstone t = new Tombstone.Builder().signal(sig).build();
      assertSame(sig, t.signal);
      assertTrue(t.hasSignal());
    }
  }

  @Nested
  class CollectionSetters {

    @Test
    void commandLine() {
      Tombstone t =
          new Tombstone.Builder()
              .commandLine(Arrays.asList("/system/bin/app", "com.example"))
              .build();
      assertEquals(2, t.commandLine.size());
      assertEquals("/system/bin/app", t.commandLine.get(0));
    }

    @Test
    void causes() {
      List<Cause> causes =
          Arrays.asList(
              new Cause("null pointer dereference", null), new Cause("second cause", null));
      Tombstone t = new Tombstone.Builder().causes(causes).build();
      assertEquals(2, t.causes.size());
      assertEquals("null pointer dereference", t.causes.get(0).humanReadable);
    }

    @Test
    void openFds() {
      List<FD> fds = Collections.singletonList(new FD(3, "/dev/null", "system", 0));
      Tombstone t = new Tombstone.Builder().openFds(fds).build();
      assertEquals(1, t.openFds.size());
      assertEquals("/dev/null", t.openFds.get(0).path);
    }
  }

  @Nested
  class ConvenienceMethods {

    @Test
    void addThread() {
      TombstoneThread thread =
          new TombstoneThread(
              42,
              "main",
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              0,
              0);
      Tombstone t = new Tombstone.Builder().addThread(thread).build();
      assertEquals(1, t.threads.size());
      assertTrue(t.threads.containsKey(42));
      assertEquals("main", t.threads.get(42).name);
    }

    @Test
    void addMultipleThreads() {
      TombstoneThread t1 =
          new TombstoneThread(
              1,
              "main",
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              0,
              0);
      TombstoneThread t2 =
          new TombstoneThread(
              2,
              "worker",
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              0,
              0);
      Tombstone t = new Tombstone.Builder().addThread(t1).addThread(t2).build();
      assertEquals(2, t.threads.size());
      assertEquals("main", t.threads.get(1).name);
      assertEquals("worker", t.threads.get(2).name);
    }

    @Test
    void addMemoryMapping() {
      MemoryMapping m =
          new MemoryMapping(0x7000L, 0x8000L, 0, true, false, true, "/system/lib/libc.so", "", 0);
      Tombstone t = new Tombstone.Builder().addMemoryMapping(m).build();
      assertEquals(1, t.memoryMappings.size());
      assertEquals("/system/lib/libc.so", t.memoryMappings.get(0).mappingName);
    }
  }

  @Nested
  class Immutability {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void builtCollectionsAreImmutable() {
      Tombstone t = new Tombstone.Builder().build();
      assertThrows(UnsupportedOperationException.class, () -> t.commandLine.add("x"));
      assertThrows(UnsupportedOperationException.class, () -> t.causes.add(null));
      assertThrows(UnsupportedOperationException.class, () -> t.crashDetails.add(null));
      assertThrows(UnsupportedOperationException.class, () -> t.threads.put(1, null));
      assertThrows(UnsupportedOperationException.class, () -> t.guestThreads.put(1, null));
      assertThrows(UnsupportedOperationException.class, () -> t.memoryMappings.add(null));
      assertThrows(UnsupportedOperationException.class, () -> t.logBuffers.add(null));
      assertThrows(UnsupportedOperationException.class, () -> t.openFds.add(null));
    }

    @Test
    void mutatingBuilderListAfterBuildDoesNotAffectTombstone() {
      Tombstone.Builder builder = new Tombstone.Builder();
      MemoryMapping m =
          new MemoryMapping(0x1000L, 0x2000L, 0, true, false, false, "libfoo.so", "", 0);
      builder.addMemoryMapping(m);
      Tombstone t = builder.build();

      // Add another mapping via builder after build
      builder.addMemoryMapping(
          new MemoryMapping(0x3000L, 0x4000L, 0, true, false, false, "libbar.so", "", 0));

      // The built tombstone should not be affected
      assertEquals(1, t.memoryMappings.size());
    }
  }

  @Nested
  class Chaining {

    @Test
    void allSettersReturnBuilder() {
      // Verify fluent API works end-to-end
      Tombstone t =
          new Tombstone.Builder()
              .arch(Architecture.ARM64)
              .pid(100)
              .tid(100)
              .uid(1000)
              .buildFingerprint("fp")
              .revision("r")
              .timestamp("ts")
              .selinuxLabel("label")
              .commandLine(Collections.singletonList("cmd"))
              .processUptime(10)
              .signal(new Signal(11, "SIGSEGV", 1, "SEGV_MAPERR", false, 0, 0, true, 0xDEADL, null))
              .abortMessage("abort")
              .crashDetails(Collections.emptyList())
              .causes(Collections.singletonList(new Cause("cause", null)))
              .threads(Collections.emptyMap())
              .guestThreads(Collections.emptyMap())
              .guestArch(Architecture.NONE)
              .memoryMappings(Collections.emptyList())
              .logBuffers(Collections.emptyList())
              .openFds(Collections.emptyList())
              .pageSize(4096)
              .hasBeen16kbMode(false)
              .stackHistoryBuffer(null)
              .build();

      assertEquals(100, t.pid);
      assertEquals("SIGSEGV", t.signal.name);
      assertEquals(1, t.causes.size());
    }
  }
}
