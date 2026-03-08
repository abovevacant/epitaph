package com.abovevacant.epitaph.wire;

import com.abovevacant.epitaph.core.*;
import com.abovevacant.epitaph.io.WireReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decoder for Android tombstone protobuf format.
 *
 * <p>This is a custom, dependency-free implementation that parses all fields from the Android
 * tombstone protobuf schema.
 *
 * <p>The tombstone protobuf schema is defined at: <a
 * href="https://android.googlesource.com/platform/system/core/+/refs/heads/main/debuggerd/proto/tombstone.proto">...</a>
 */
public final class TombstoneDecoder {

  // Tombstone field numbers
  private static final int TOMBSTONE_ARCH = 1;
  private static final int TOMBSTONE_BUILD_FINGERPRINT = 2;
  private static final int TOMBSTONE_REVISION = 3;
  private static final int TOMBSTONE_TIMESTAMP = 4;
  private static final int TOMBSTONE_PID = 5;
  private static final int TOMBSTONE_TID = 6;
  private static final int TOMBSTONE_UID = 7;
  private static final int TOMBSTONE_SELINUX_LABEL = 8;
  private static final int TOMBSTONE_COMMAND_LINE = 9;
  private static final int TOMBSTONE_SIGNAL_INFO = 10;
  private static final int TOMBSTONE_ABORT_MESSAGE = 14;
  private static final int TOMBSTONE_CAUSES = 15;
  private static final int TOMBSTONE_THREADS = 16;
  private static final int TOMBSTONE_MEMORY_MAPPINGS = 17;
  private static final int TOMBSTONE_LOG_BUFFERS = 18;
  private static final int TOMBSTONE_OPEN_FDS = 19;
  private static final int TOMBSTONE_PROCESS_UPTIME = 20;
  private static final int TOMBSTONE_CRASH_DETAILS = 21;
  private static final int TOMBSTONE_PAGE_SIZE = 22;
  private static final int TOMBSTONE_HAS_BEEN_16KB_MODE = 23;
  private static final int TOMBSTONE_GUEST_ARCH = 24;
  private static final int TOMBSTONE_GUEST_THREADS = 25;
  private static final int TOMBSTONE_STACK_HISTORY_BUFFER = 26;

  // Signal field numbers
  private static final int SIGNAL_NUMBER = 1;
  private static final int SIGNAL_NAME = 2;
  private static final int SIGNAL_CODE = 3;
  private static final int SIGNAL_CODE_NAME = 4;
  private static final int SIGNAL_HAS_SENDER = 5;
  private static final int SIGNAL_SENDER_UID = 6;
  private static final int SIGNAL_SENDER_PID = 7;
  private static final int SIGNAL_HAS_FAULT_ADDRESS = 8;
  private static final int SIGNAL_FAULT_ADDRESS = 9;
  private static final int SIGNAL_FAULT_ADJACENT_METADATA = 10;

  // Thread field numbers
  private static final int THREAD_ID = 1;
  private static final int THREAD_NAME = 2;
  private static final int THREAD_REGISTERS = 3;
  private static final int THREAD_CURRENT_BACKTRACE = 4;
  private static final int THREAD_MEMORY_DUMP = 5;
  private static final int THREAD_TAGGED_ADDR_CTRL = 6;
  private static final int THREAD_BACKTRACE_NOTE = 7;
  private static final int THREAD_PAC_ENABLED_KEYS = 8;
  private static final int THREAD_UNREADABLE_ELF_FILES = 9;

  // BacktraceFrame field numbers
  private static final int FRAME_REL_PC = 1;
  private static final int FRAME_PC = 2;
  private static final int FRAME_SP = 3;
  private static final int FRAME_FUNCTION_NAME = 4;
  private static final int FRAME_FUNCTION_OFFSET = 5;
  private static final int FRAME_FILE_NAME = 6;
  private static final int FRAME_FILE_MAP_OFFSET = 7;
  private static final int FRAME_BUILD_ID = 8;

  // Register field numbers
  private static final int REGISTER_NAME = 1;
  private static final int REGISTER_U64 = 2;

  // MemoryMapping field numbers
  private static final int MAPPING_BEGIN_ADDRESS = 1;
  private static final int MAPPING_END_ADDRESS = 2;
  private static final int MAPPING_OFFSET = 3;
  private static final int MAPPING_READ = 4;
  private static final int MAPPING_WRITE = 5;
  private static final int MAPPING_EXECUTE = 6;
  private static final int MAPPING_NAME = 7;
  private static final int MAPPING_BUILD_ID = 8;
  private static final int MAPPING_LOAD_BIAS = 9;

  // MemoryDump field numbers
  private static final int MEMORY_DUMP_REGISTER_NAME = 1;
  private static final int MEMORY_DUMP_MAPPING_NAME = 2;
  private static final int MEMORY_DUMP_BEGIN_ADDRESS = 3;
  private static final int MEMORY_DUMP_MEMORY = 4;
  private static final int MEMORY_DUMP_ARM_MTE_METADATA = 6;

  // ArmMTEMetadata field numbers
  private static final int ARM_MTE_MEMORY_TAGS = 1;

  // Cause field numbers
  private static final int CAUSE_HUMAN_READABLE = 1;
  private static final int CAUSE_MEMORY_ERROR = 2;

  // MemoryError field numbers
  private static final int MEMORY_ERROR_TOOL = 1;
  private static final int MEMORY_ERROR_TYPE = 2;
  private static final int MEMORY_ERROR_HEAP = 3;

  // HeapObject field numbers
  private static final int HEAP_OBJECT_ADDRESS = 1;
  private static final int HEAP_OBJECT_SIZE = 2;
  private static final int HEAP_OBJECT_ALLOCATION_TID = 3;
  private static final int HEAP_OBJECT_ALLOCATION_BACKTRACE = 4;
  private static final int HEAP_OBJECT_DEALLOCATION_TID = 5;
  private static final int HEAP_OBJECT_DEALLOCATION_BACKTRACE = 6;

  // FD field numbers
  private static final int FD_FD = 1;
  private static final int FD_PATH = 2;
  private static final int FD_OWNER = 3;
  private static final int FD_TAG = 4;

  // LogBuffer field numbers
  private static final int LOG_BUFFER_NAME = 1;
  private static final int LOG_BUFFER_LOGS = 2;

  // LogMessage field numbers
  private static final int LOG_MESSAGE_TIMESTAMP = 1;
  private static final int LOG_MESSAGE_PID = 2;
  private static final int LOG_MESSAGE_TID = 3;
  private static final int LOG_MESSAGE_PRIORITY = 4;
  private static final int LOG_MESSAGE_TAG = 5;
  private static final int LOG_MESSAGE_MESSAGE = 6;

  // CrashDetail field numbers
  private static final int CRASH_DETAIL_NAME = 1;
  private static final int CRASH_DETAIL_DATA = 2;

  // StackHistoryBuffer field numbers
  private static final int STACK_HISTORY_BUFFER_TID = 1;
  private static final int STACK_HISTORY_BUFFER_ENTRIES = 2;

  // StackHistoryBufferEntry field numbers
  private static final int STACK_HISTORY_BUFFER_ENTRY_ADDR = 1;
  private static final int STACK_HISTORY_BUFFER_ENTRY_FP = 2;
  private static final int STACK_HISTORY_BUFFER_ENTRY_TAG = 3;

  // Map entry field numbers (protobuf maps are encoded as repeated messages)
  private static final int MAP_KEY = 1;
  private static final int MAP_VALUE = 2;

  private TombstoneDecoder() {}

  private static void expectVarint(final int fieldNumber, final int wireType) throws IOException {
    WireReader.expectWireType(fieldNumber, WireReader.WIRETYPE_VARINT, wireType);
  }

  private static void expectBytes(final int fieldNumber, final int wireType) throws IOException {
    WireReader.expectWireType(fieldNumber, WireReader.WIRETYPE_LENGTH_DELIMITED, wireType);
  }

  /**
   * Parses a tombstone from an InputStream.
   *
   * @param input the input stream containing the protobuf data
   * @return the parsed Tombstone
   * @throws IOException if parsing fails
   */
  public static Tombstone decode(final InputStream input) throws IOException {
    return decode(WireReader.fromInputStream(input));
  }

  /**
   * Parses a tombstone from raw bytes.
   *
   * @param data the protobuf data
   * @return the parsed Tombstone
   * @throws IOException if parsing fails
   */
  public static Tombstone decode(final byte[] data) throws IOException {
    return decode(new WireReader(data));
  }

  private static Tombstone decode(final WireReader reader) throws IOException {
    Architecture arch = Architecture.NONE;
    Architecture guestArch = Architecture.NONE;
    String buildFingerprint = "";
    String revision = "";
    String timestamp = "";
    int pid = 0;
    int tid = 0;
    int uid = 0;
    String selinuxLabel = "";
    final List<String> commandLine = new ArrayList<>();
    int processUptime = 0;
    Signal signal = null;
    String abortMessage = "";
    final List<CrashDetail> crashDetails = new ArrayList<>();
    final List<Cause> causes = new ArrayList<>();
    final Map<Integer, TombstoneThread> threads = new HashMap<>();
    final Map<Integer, TombstoneThread> guestThreads = new HashMap<>();
    final List<MemoryMapping> memoryMappings = new ArrayList<>();
    final List<LogBuffer> logBuffers = new ArrayList<>();
    final List<FD> openFds = new ArrayList<>();
    int pageSize = 0;
    boolean hasBeen16kbMode = false;
    StackHistoryBuffer stackHistoryBuffer = null;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case TOMBSTONE_ARCH:
          expectVarint(fieldNumber, wireType);
          arch = Architecture.fromValue(reader.readVarInt32());
          break;
        case TOMBSTONE_GUEST_ARCH:
          expectVarint(fieldNumber, wireType);
          guestArch = Architecture.fromValue(reader.readVarInt32());
          break;
        case TOMBSTONE_BUILD_FINGERPRINT:
          expectBytes(fieldNumber, wireType);
          buildFingerprint = reader.readString();
          break;
        case TOMBSTONE_REVISION:
          expectBytes(fieldNumber, wireType);
          revision = reader.readString();
          break;
        case TOMBSTONE_TIMESTAMP:
          expectBytes(fieldNumber, wireType);
          timestamp = reader.readString();
          break;
        case TOMBSTONE_PID:
          expectVarint(fieldNumber, wireType);
          pid = reader.readVarInt32();
          break;
        case TOMBSTONE_TID:
          expectVarint(fieldNumber, wireType);
          tid = reader.readVarInt32();
          break;
        case TOMBSTONE_UID:
          expectVarint(fieldNumber, wireType);
          uid = reader.readVarInt32();
          break;
        case TOMBSTONE_SELINUX_LABEL:
          expectBytes(fieldNumber, wireType);
          selinuxLabel = reader.readString();
          break;
        case TOMBSTONE_COMMAND_LINE:
          expectBytes(fieldNumber, wireType);
          commandLine.add(reader.readString());
          break;
        case TOMBSTONE_PROCESS_UPTIME:
          expectVarint(fieldNumber, wireType);
          processUptime = reader.readVarInt32();
          break;
        case TOMBSTONE_SIGNAL_INFO:
          expectBytes(fieldNumber, wireType);
          signal = decodeSignal(reader.readMessage());
          break;
        case TOMBSTONE_ABORT_MESSAGE:
          expectBytes(fieldNumber, wireType);
          abortMessage = reader.readString();
          break;
        case TOMBSTONE_CRASH_DETAILS:
          expectBytes(fieldNumber, wireType);
          crashDetails.add(decodeCrashDetail(reader.readMessage()));
          break;
        case TOMBSTONE_CAUSES:
          expectBytes(fieldNumber, wireType);
          causes.add(decodeCause(reader.readMessage()));
          break;
        case TOMBSTONE_THREADS:
          expectBytes(fieldNumber, wireType);
          decodeThreadMapEntry(reader.readMessage(), threads);
          break;
        case TOMBSTONE_GUEST_THREADS:
          expectBytes(fieldNumber, wireType);
          decodeThreadMapEntry(reader.readMessage(), guestThreads);
          break;
        case TOMBSTONE_MEMORY_MAPPINGS:
          expectBytes(fieldNumber, wireType);
          memoryMappings.add(decodeMemoryMapping(reader.readMessage()));
          break;
        case TOMBSTONE_LOG_BUFFERS:
          expectBytes(fieldNumber, wireType);
          logBuffers.add(decodeLogBuffer(reader.readMessage()));
          break;
        case TOMBSTONE_OPEN_FDS:
          expectBytes(fieldNumber, wireType);
          openFds.add(decodeFD(reader.readMessage()));
          break;
        case TOMBSTONE_PAGE_SIZE:
          expectVarint(fieldNumber, wireType);
          pageSize = reader.readVarInt32();
          break;
        case TOMBSTONE_HAS_BEEN_16KB_MODE:
          expectVarint(fieldNumber, wireType);
          hasBeen16kbMode = reader.readBool();
          break;
        case TOMBSTONE_STACK_HISTORY_BUFFER:
          expectBytes(fieldNumber, wireType);
          stackHistoryBuffer = decodeStackHistoryBuffer(reader.readMessage());
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

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
        commandLine,
        processUptime,
        signal,
        abortMessage,
        crashDetails,
        causes,
        threads,
        guestThreads,
        memoryMappings,
        logBuffers,
        openFds,
        pageSize,
        hasBeen16kbMode,
        stackHistoryBuffer);
  }

  private static Signal decodeSignal(final WireReader reader) throws IOException {
    int number = 0;
    String name = "";
    int code = 0;
    String codeName = "";
    boolean hasSender = false;
    int senderUid = 0;
    int senderPid = 0;
    boolean hasFaultAddress = false;
    long faultAddress = 0;
    MemoryDump faultAdjacentMetadata = null;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case SIGNAL_NUMBER:
          expectVarint(fieldNumber, wireType);
          number = reader.readVarInt32();
          break;
        case SIGNAL_NAME:
          expectBytes(fieldNumber, wireType);
          name = reader.readString();
          break;
        case SIGNAL_CODE:
          expectVarint(fieldNumber, wireType);
          code = reader.readVarInt32();
          break;
        case SIGNAL_CODE_NAME:
          expectBytes(fieldNumber, wireType);
          codeName = reader.readString();
          break;
        case SIGNAL_HAS_SENDER:
          expectVarint(fieldNumber, wireType);
          hasSender = reader.readBool();
          break;
        case SIGNAL_SENDER_UID:
          expectVarint(fieldNumber, wireType);
          senderUid = reader.readVarInt32();
          break;
        case SIGNAL_SENDER_PID:
          expectVarint(fieldNumber, wireType);
          senderPid = reader.readVarInt32();
          break;
        case SIGNAL_HAS_FAULT_ADDRESS:
          expectVarint(fieldNumber, wireType);
          hasFaultAddress = reader.readBool();
          break;
        case SIGNAL_FAULT_ADDRESS:
          expectVarint(fieldNumber, wireType);
          faultAddress = reader.readVarInt();
          break;
        case SIGNAL_FAULT_ADJACENT_METADATA:
          expectBytes(fieldNumber, wireType);
          faultAdjacentMetadata = decodeMemoryDump(reader.readMessage());
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new Signal(
        number,
        name,
        code,
        codeName,
        hasSender,
        senderUid,
        senderPid,
        hasFaultAddress,
        faultAddress,
        faultAdjacentMetadata);
  }

  private static void decodeThreadMapEntry(
      final WireReader reader, final Map<Integer, TombstoneThread> threads) throws IOException {
    int key = 0;
    TombstoneThread value = null;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case MAP_KEY:
          expectVarint(fieldNumber, wireType);
          key = reader.readVarInt32();
          break;
        case MAP_VALUE:
          expectBytes(fieldNumber, wireType);
          value = decodeThread(reader.readMessage());
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    if (value != null) {
      threads.put(key, value);
    }
  }

  private static TombstoneThread decodeThread(final WireReader reader) throws IOException {
    int id = 0;
    String name = "";
    final List<Register> registers = new ArrayList<>();
    final List<String> backtraceNote = new ArrayList<>();
    final List<String> unreadableElfFiles = new ArrayList<>();
    final List<BacktraceFrame> backtrace = new ArrayList<>();
    final List<MemoryDump> memoryDump = new ArrayList<>();
    long taggedAddrCtrl = 0;
    long pacEnabledKeys = 0;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case THREAD_ID:
          expectVarint(fieldNumber, wireType);
          id = reader.readVarInt32();
          break;
        case THREAD_NAME:
          expectBytes(fieldNumber, wireType);
          name = reader.readString();
          break;
        case THREAD_REGISTERS:
          expectBytes(fieldNumber, wireType);
          registers.add(decodeRegister(reader.readMessage()));
          break;
        case THREAD_BACKTRACE_NOTE:
          expectBytes(fieldNumber, wireType);
          backtraceNote.add(reader.readString());
          break;
        case THREAD_UNREADABLE_ELF_FILES:
          expectBytes(fieldNumber, wireType);
          unreadableElfFiles.add(reader.readString());
          break;
        case THREAD_CURRENT_BACKTRACE:
          expectBytes(fieldNumber, wireType);
          backtrace.add(decodeBacktraceFrame(reader.readMessage()));
          break;
        case THREAD_MEMORY_DUMP:
          expectBytes(fieldNumber, wireType);
          memoryDump.add(decodeMemoryDump(reader.readMessage()));
          break;
        case THREAD_TAGGED_ADDR_CTRL:
          expectVarint(fieldNumber, wireType);
          taggedAddrCtrl = reader.readVarInt();
          break;
        case THREAD_PAC_ENABLED_KEYS:
          expectVarint(fieldNumber, wireType);
          pacEnabledKeys = reader.readVarInt();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new TombstoneThread(
        id,
        name,
        registers,
        backtraceNote,
        unreadableElfFiles,
        backtrace,
        memoryDump,
        taggedAddrCtrl,
        pacEnabledKeys);
  }

  private static BacktraceFrame decodeBacktraceFrame(final WireReader reader) throws IOException {
    long relPc = 0;
    long pc = 0;
    long sp = 0;
    String functionName = "";
    long functionOffset = 0;
    String fileName = "";
    long fileMapOffset = 0;
    String buildId = "";

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case FRAME_REL_PC:
          expectVarint(fieldNumber, wireType);
          relPc = reader.readVarInt();
          break;
        case FRAME_PC:
          expectVarint(fieldNumber, wireType);
          pc = reader.readVarInt();
          break;
        case FRAME_SP:
          expectVarint(fieldNumber, wireType);
          sp = reader.readVarInt();
          break;
        case FRAME_FUNCTION_NAME:
          expectBytes(fieldNumber, wireType);
          functionName = reader.readString();
          break;
        case FRAME_FUNCTION_OFFSET:
          expectVarint(fieldNumber, wireType);
          functionOffset = reader.readVarInt();
          break;
        case FRAME_FILE_NAME:
          expectBytes(fieldNumber, wireType);
          fileName = reader.readString();
          break;
        case FRAME_FILE_MAP_OFFSET:
          expectVarint(fieldNumber, wireType);
          fileMapOffset = reader.readVarInt();
          break;
        case FRAME_BUILD_ID:
          expectBytes(fieldNumber, wireType);
          buildId = reader.readString();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new BacktraceFrame(
        relPc, pc, sp, functionName, functionOffset, fileName, fileMapOffset, buildId);
  }

  private static Register decodeRegister(final WireReader reader) throws IOException {
    String name = "";
    long value = 0;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case REGISTER_NAME:
          expectBytes(fieldNumber, wireType);
          name = reader.readString();
          break;
        case REGISTER_U64:
          expectVarint(fieldNumber, wireType);
          value = reader.readVarInt();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new Register(name, value);
  }

  private static MemoryMapping decodeMemoryMapping(final WireReader reader) throws IOException {
    long beginAddress = 0;
    long endAddress = 0;
    long offset = 0;
    boolean read = false;
    boolean write = false;
    boolean execute = false;
    String mappingName = "";
    String buildId = "";
    long loadBias = 0;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case MAPPING_BEGIN_ADDRESS:
          expectVarint(fieldNumber, wireType);
          beginAddress = reader.readVarInt();
          break;
        case MAPPING_END_ADDRESS:
          expectVarint(fieldNumber, wireType);
          endAddress = reader.readVarInt();
          break;
        case MAPPING_OFFSET:
          expectVarint(fieldNumber, wireType);
          offset = reader.readVarInt();
          break;
        case MAPPING_READ:
          expectVarint(fieldNumber, wireType);
          read = reader.readBool();
          break;
        case MAPPING_WRITE:
          expectVarint(fieldNumber, wireType);
          write = reader.readBool();
          break;
        case MAPPING_EXECUTE:
          expectVarint(fieldNumber, wireType);
          execute = reader.readBool();
          break;
        case MAPPING_NAME:
          expectBytes(fieldNumber, wireType);
          mappingName = reader.readString();
          break;
        case MAPPING_BUILD_ID:
          expectBytes(fieldNumber, wireType);
          buildId = reader.readString();
          break;
        case MAPPING_LOAD_BIAS:
          expectVarint(fieldNumber, wireType);
          loadBias = reader.readVarInt();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new MemoryMapping(
        beginAddress, endAddress, offset, read, write, execute, mappingName, buildId, loadBias);
  }

  private static MemoryDump decodeMemoryDump(final WireReader reader) throws IOException {
    String registerName = "";
    String mappingName = "";
    long beginAddress = 0;
    byte[] memory = new byte[0];
    ArmMTEMetadata armMteMetadata = null;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case MEMORY_DUMP_REGISTER_NAME:
          expectBytes(fieldNumber, wireType);
          registerName = reader.readString();
          break;
        case MEMORY_DUMP_MAPPING_NAME:
          expectBytes(fieldNumber, wireType);
          mappingName = reader.readString();
          break;
        case MEMORY_DUMP_BEGIN_ADDRESS:
          expectVarint(fieldNumber, wireType);
          beginAddress = reader.readVarInt();
          break;
        case MEMORY_DUMP_MEMORY:
          expectBytes(fieldNumber, wireType);
          memory = reader.readBytes();
          break;
        case MEMORY_DUMP_ARM_MTE_METADATA:
          expectBytes(fieldNumber, wireType);
          armMteMetadata = decodeArmMTEMetadata(reader.readMessage());
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new MemoryDump(registerName, mappingName, beginAddress, memory, armMteMetadata);
  }

  static ArmMTEMetadata decodeArmMTEMetadata(final WireReader reader) throws IOException {
    byte[] memoryTags = new byte[0];

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      //noinspection SwitchStatementWithTooFewBranches
      switch (fieldNumber) {
        case ARM_MTE_MEMORY_TAGS:
          expectBytes(fieldNumber, wireType);
          memoryTags = reader.readBytes();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new ArmMTEMetadata(memoryTags);
  }

  private static Cause decodeCause(final WireReader reader) throws IOException {
    String humanReadable = "";
    MemoryError memoryError = null;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case CAUSE_HUMAN_READABLE:
          expectBytes(fieldNumber, wireType);
          humanReadable = reader.readString();
          break;
        case CAUSE_MEMORY_ERROR:
          expectBytes(fieldNumber, wireType);
          memoryError = decodeMemoryError(reader.readMessage());
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new Cause(humanReadable, memoryError);
  }

  static MemoryError decodeMemoryError(final WireReader reader) throws IOException {
    MemoryError.Tool tool = MemoryError.Tool.GWP_ASAN;
    MemoryError.Type type = MemoryError.Type.UNKNOWN;
    HeapObject heap = null;

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case MEMORY_ERROR_TOOL:
          expectVarint(fieldNumber, wireType);
          tool = MemoryError.Tool.fromValue(reader.readVarInt32());
          break;
        case MEMORY_ERROR_TYPE:
          expectVarint(fieldNumber, wireType);
          type = MemoryError.Type.fromValue(reader.readVarInt32());
          break;
        case MEMORY_ERROR_HEAP:
          expectBytes(fieldNumber, wireType);
          heap = decodeHeapObject(reader.readMessage());
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new MemoryError(tool, type, heap);
  }

  static HeapObject decodeHeapObject(final WireReader reader) throws IOException {
    long address = 0;
    long size = 0;
    long allocationTid = 0;
    final List<BacktraceFrame> allocationBacktrace = new ArrayList<>();
    long deallocationTid = 0;
    final List<BacktraceFrame> deallocationBacktrace = new ArrayList<>();

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case HEAP_OBJECT_ADDRESS:
          expectVarint(fieldNumber, wireType);
          address = reader.readVarInt();
          break;
        case HEAP_OBJECT_SIZE:
          expectVarint(fieldNumber, wireType);
          size = reader.readVarInt();
          break;
        case HEAP_OBJECT_ALLOCATION_TID:
          expectVarint(fieldNumber, wireType);
          allocationTid = reader.readVarInt();
          break;
        case HEAP_OBJECT_ALLOCATION_BACKTRACE:
          expectBytes(fieldNumber, wireType);
          allocationBacktrace.add(decodeBacktraceFrame(reader.readMessage()));
          break;
        case HEAP_OBJECT_DEALLOCATION_TID:
          expectVarint(fieldNumber, wireType);
          deallocationTid = reader.readVarInt();
          break;
        case HEAP_OBJECT_DEALLOCATION_BACKTRACE:
          expectBytes(fieldNumber, wireType);
          deallocationBacktrace.add(decodeBacktraceFrame(reader.readMessage()));
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new HeapObject(
        address, size, allocationTid, allocationBacktrace, deallocationTid, deallocationBacktrace);
  }

  private static FD decodeFD(final WireReader reader) throws IOException {
    int fd = 0;
    String path = "";
    String owner = "";
    long tag = 0;

    int wireTag;
    while ((wireTag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(wireTag);
      final int wireType = WireReader.getWireType(wireTag);

      switch (fieldNumber) {
        case FD_FD:
          expectVarint(fieldNumber, wireType);
          fd = reader.readVarInt32();
          break;
        case FD_PATH:
          expectBytes(fieldNumber, wireType);
          path = reader.readString();
          break;
        case FD_OWNER:
          expectBytes(fieldNumber, wireType);
          owner = reader.readString();
          break;
        case FD_TAG:
          expectVarint(fieldNumber, wireType);
          tag = reader.readVarInt();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new FD(fd, path, owner, tag);
  }

  static LogBuffer decodeLogBuffer(final WireReader reader) throws IOException {
    String name = "";
    final List<LogMessage> logs = new ArrayList<>();

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case LOG_BUFFER_NAME:
          expectBytes(fieldNumber, wireType);
          name = reader.readString();
          break;
        case LOG_BUFFER_LOGS:
          expectBytes(fieldNumber, wireType);
          logs.add(decodeLogMessage(reader.readMessage()));
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new LogBuffer(name, logs);
  }

  static LogMessage decodeLogMessage(final WireReader reader) throws IOException {
    String timestamp = "";
    int pid = 0;
    int tid = 0;
    int priority = 0;
    String tag = "";
    String message = "";

    int wireTag;
    while ((wireTag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(wireTag);
      final int wireType = WireReader.getWireType(wireTag);

      switch (fieldNumber) {
        case LOG_MESSAGE_TIMESTAMP:
          expectBytes(fieldNumber, wireType);
          timestamp = reader.readString();
          break;
        case LOG_MESSAGE_PID:
          expectVarint(fieldNumber, wireType);
          pid = reader.readVarInt32();
          break;
        case LOG_MESSAGE_TID:
          expectVarint(fieldNumber, wireType);
          tid = reader.readVarInt32();
          break;
        case LOG_MESSAGE_PRIORITY:
          expectVarint(fieldNumber, wireType);
          priority = reader.readVarInt32();
          break;
        case LOG_MESSAGE_TAG:
          expectBytes(fieldNumber, wireType);
          tag = reader.readString();
          break;
        case LOG_MESSAGE_MESSAGE:
          expectBytes(fieldNumber, wireType);
          message = reader.readString();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new LogMessage(timestamp, pid, tid, priority, tag, message);
  }

  static CrashDetail decodeCrashDetail(final WireReader reader) throws IOException {
    byte[] name = new byte[0];
    byte[] data = new byte[0];

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case CRASH_DETAIL_NAME:
          expectBytes(fieldNumber, wireType);
          name = reader.readBytes();
          break;
        case CRASH_DETAIL_DATA:
          expectBytes(fieldNumber, wireType);
          data = reader.readBytes();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new CrashDetail(name, data);
  }

  static StackHistoryBuffer decodeStackHistoryBuffer(final WireReader reader) throws IOException {
    long tid = 0;
    final List<StackHistoryBufferEntry> entries = new ArrayList<>();

    int tag;
    while ((tag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(tag);
      final int wireType = WireReader.getWireType(tag);

      switch (fieldNumber) {
        case STACK_HISTORY_BUFFER_TID:
          expectVarint(fieldNumber, wireType);
          tid = reader.readVarInt();
          break;
        case STACK_HISTORY_BUFFER_ENTRIES:
          expectBytes(fieldNumber, wireType);
          entries.add(decodeStackHistoryBufferEntry(reader.readMessage()));
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new StackHistoryBuffer(tid, entries);
  }

  static StackHistoryBufferEntry decodeStackHistoryBufferEntry(final WireReader reader)
      throws IOException {
    BacktraceFrame addr = null;
    long fp = 0;
    long tag = 0;

    int wireTag;
    while ((wireTag = reader.readTag()) != 0) {
      final int fieldNumber = WireReader.getFieldNumber(wireTag);
      final int wireType = WireReader.getWireType(wireTag);

      switch (fieldNumber) {
        case STACK_HISTORY_BUFFER_ENTRY_ADDR:
          expectBytes(fieldNumber, wireType);
          addr = decodeBacktraceFrame(reader.readMessage());
          break;
        case STACK_HISTORY_BUFFER_ENTRY_FP:
          expectVarint(fieldNumber, wireType);
          fp = reader.readVarInt();
          break;
        case STACK_HISTORY_BUFFER_ENTRY_TAG:
          expectVarint(fieldNumber, wireType);
          tag = reader.readVarInt();
          break;
        default:
          reader.skipField(wireType);
          break;
      }
    }

    return new StackHistoryBufferEntry(addr, fp, tag);
  }
}
