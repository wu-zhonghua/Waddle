/* SPDX-License-Identifier: GPL-3.0-only
 * Own the Codex process group so closing Waddle's Process also closes helpers.
 * No privilege transitions, network transport, or cross-app process access.
 */
#include <errno.h>
#include <dirent.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static volatile sig_atomic_t child_pid = -1;

/* Shell tools may create new sessions. A subreaper also owns those orphans;
 * killing only the original process group would leave stopped commands running. */
static void kill_adopted_children(void) {
    DIR *directory = opendir("/proc");
    if (!directory) return;
    struct dirent *entry;
    while ((entry = readdir(directory))) {
        char *end;
        long pid = strtol(entry->d_name, &end, 10);
        if (*end || pid <= 0 || pid == getpid()) continue;
        char path[64], stat[1024];
        snprintf(path, sizeof(path), "/proc/%ld/stat", pid);
        FILE *file = fopen(path, "r");
        if (!file) continue;
        char *line = fgets(stat, sizeof(stat), file);
        fclose(file);
        if (!line) continue;
        char *close = strrchr(stat, ')');
        char state;
        long parent;
        if (close && sscanf(close + 1, " %c %ld", &state, &parent) == 2 && parent == getpid())
            kill((pid_t)pid, SIGKILL);
    }
    closedir(directory);
}
static void stop(int signal_number) {
    if (child_pid > 0) {
        int sig = signal_number == SIGALRM ? SIGKILL : SIGTERM;
        kill(-child_pid, sig);
        kill(child_pid, sig);
        if (sig == SIGTERM) alarm(3);
    }
}

int main(int argc, char **argv) {
    if (argc < 2) return 64;
    if (prctl(PR_SET_CHILD_SUBREAPER, 1) < 0) return 126;
    struct sigaction action = {0};
    action.sa_handler = stop;
    sigemptyset(&action.sa_mask);
    sigaction(SIGTERM, &action, NULL);
    sigaction(SIGINT, &action, NULL);
    sigaction(SIGHUP, &action, NULL);
    sigaction(SIGALRM, &action, NULL);
    pid_t owner = getppid();
    prctl(PR_SET_PDEATHSIG, SIGTERM);
    if (getppid() != owner) return 1;
    pid_t pid = fork();
    if (pid < 0) return 1;
    if (pid == 0) {
        signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL);
        signal(SIGHUP, SIG_DFL); signal(SIGALRM, SIG_DFL);
        if (setsid() < 0) _exit(126);
        prctl(PR_SET_PDEATHSIG, SIGKILL);
        if (getppid() == 1) _exit(126);
        execv(argv[1], argv + 1);
        _exit(127);
    }
    child_pid = pid;
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) if (errno != EINTR) break;
    child_pid = -1;
    alarm(0);
    kill(-pid, SIGKILL);
    // Reap in waves: killing an adopted parent may expose another generation.
    for (int attempt = 0; attempt < 400; ++attempt) {
        pid_t reaped;
        do { reaped = waitpid(-1, NULL, WNOHANG); } while (reaped > 0);
        if (reaped < 0 && errno == ECHILD) break;
        kill_adopted_children();
        usleep(10000);
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}
