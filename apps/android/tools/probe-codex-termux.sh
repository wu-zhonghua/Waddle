#!/data/data/com.termux/files/usr/bin/bash
# Run inside a disposable Termux terminal after: pkg install codex
# No login, model request, network listener, or sandbox bypass is used.
set -euo pipefail
umask 077

command -v codex >/dev/null
probe=$(mktemp -d "$HOME/waddle-codex-probe.XXXXXXXX")
mkdir -p "$probe/workspace" "$probe/codex-home"
cd "$probe/workspace"
printf 'Results: %s\n' "$probe"
{
    codex --version
    getprop ro.build.version.release
    getprop ro.product.cpu.abi
    id
    cat /proc/self/attr/current
    printf '\n'
    command -v codex-code-mode-host
} > "$probe/environment.txt"

coproc WADDLE_CODEX {
    env CODEX_HOME="$probe/codex-home" codex app-server 2> "$probe/server.stderr"
}
server_pid=$WADDLE_CODEX_PID
exec {server_in}>&"${WADDLE_CODEX[1]}"
exec {server_out}<&"${WADDLE_CODEX[0]}"
cleanup() {
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
}
trap cleanup EXIT

send() { printf '%s\n' "$1" >&"$server_in"; }
rpc() {
    local request_id=$1 line
    send "$2"
    while IFS= read -r -t 20 -u "$server_out" line; do
        printf '%s\n' "$line" >> "$probe/protocol.jsonl"
        if [[ $line == *\"id\":$request_id,* ]]; then
            printf '%s\n' "$line" > "$probe/response-$request_id.json"
            return
        fi
    done
    printf 'No response to request %s\n' "$request_id" >&2
    exit 1
}

rpc 1 '{"id":1,"method":"initialize","params":{"clientInfo":{"name":"waddle_android_probe","version":"0.1.0"},"capabilities":{"experimentalApi":true}}}'
send '{"method":"initialized","params":{}}'
rpc 10 '{"id":10,"method":"command/exec","params":{"command":["/system/bin/sh","-c","printf ANDROID_COMMAND_OK; id; pwd"],"sandboxPolicy":{"type":"readOnly"},"timeoutMs":10000}}'
# Every write below stays inside the newly created probe tree, including the
# intentional parent-directory write used to test the workspace boundary.
rpc 11 '{"id":11,"method":"command/exec","params":{"command":["/system/bin/sh","-c","printf unexpected > readonly-sentinel.txt"],"sandboxPolicy":{"type":"readOnly"},"timeoutMs":10000}}'
rpc 12 '{"id":12,"method":"command/exec","params":{"command":["/system/bin/sh","-c","printf expected > workspace-sentinel.txt"],"sandboxPolicy":{"type":"workspaceWrite"},"timeoutMs":10000}}'
rpc 13 '{"id":13,"method":"command/exec","params":{"command":["/system/bin/sh","-c","printf unexpected > ../outside-sentinel.txt"],"sandboxPolicy":{"type":"workspaceWrite"},"timeoutMs":10000}}'

if ! grep -q ANDROID_COMMAND_OK "$probe/response-10.json" || [[ ! -f workspace-sentinel.txt ]]; then
    printf 'Runtime execution failed; inspect responses in %s\n' "$probe" | tee "$probe/result.txt"
    exit 1
fi
if [[ -f readonly-sentinel.txt || -f ../outside-sentinel.txt ]]; then
    printf 'RUNTIME_OK; WORKSPACE_SANDBOX_NOT_ENFORCED\n' | tee "$probe/result.txt"
    exit 2
fi
printf 'RUNTIME_OK; PROBE_WRITES_CONFINED\n' | tee "$probe/result.txt"
# Passing this narrow probe is not a complete sandbox security audit.
