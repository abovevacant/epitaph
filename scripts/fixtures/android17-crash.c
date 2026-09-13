// Controlled native crash for the Android 17 tombstone fixture.
// Run only on a dedicated, rootable test emulator. See src/test/resources/README.md.
#include <android/set_abort_message.h>
#include <linux/bpf.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/eventfd.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

static pthread_barrier_t ready;

static void* worker(void* ignored) {
    (void)ignored;
    pthread_setname_np(pthread_self(), "epitaph-worker");
    pthread_barrier_wait(&ready);
    for (;;) pause();
    return NULL;
}

__attribute__((noinline)) static void epitaph_fixture_crash(void) {
    android_set_abort_message("epitaph Android 17 fixture: deliberate SIGABRT");
    abort();
}

int main(void) {
    pthread_t thread;
    // An unattached, unpinned BPF program exercises FD.details (prog_id).
    // It is freed when the crashing process's descriptor is closed.
    const struct bpf_insn instructions[] = {
        {.code = BPF_ALU64 | BPF_MOV | BPF_K, .dst_reg = BPF_REG_0, .imm = 0},
        {.code = BPF_JMP | BPF_EXIT},
    };
    union bpf_attr program = {
        .prog_type = BPF_PROG_TYPE_SOCKET_FILTER,
        .insn_cnt = 2,
        .insns = (uint64_t)(uintptr_t)instructions,
        .license = (uint64_t)(uintptr_t)"GPL",
        .prog_name = "epitaph_fixture",
    };
    int bpf_fd = syscall(__NR_bpf, BPF_PROG_LOAD, &program, sizeof(program));
    if (bpf_fd < 0) {
        perror("BPF_PROG_LOAD (requires a rootable test emulator)");
        return 5;
    }
    int fd = eventfd(17, EFD_CLOEXEC);
    long page_size = sysconf(_SC_PAGESIZE);
    void* memory = mmap(NULL, page_size, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (fd < 0 || memory == MAP_FAILED) return 1;
    // PR_SET_VMA / PR_SET_VMA_ANON_NAME, available on Android kernels.
    if (prctl(0x53564d41, 0, memory, page_size, "epitaph-fixture") != 0) return 2;
    *(volatile unsigned char*)memory = 17;
    if (pthread_barrier_init(&ready, NULL, 2) != 0) return 3;
    if (pthread_create(&thread, NULL, worker, NULL) != 0) return 4;
    pthread_barrier_wait(&ready);
    printf("epitaph fixture: pid=%d ppid=%d bpf_fd=%d eventfd=%d page_size=%ld\n",
           getpid(), getppid(), bpf_fd, fd, page_size);
    fflush(stdout);
    epitaph_fixture_crash();
}
