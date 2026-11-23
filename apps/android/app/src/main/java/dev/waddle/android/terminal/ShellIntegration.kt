package dev.waddle.android.terminal

import java.io.File

/** The queue is consumed by Readline, not pasted into whichever program happens to own stdin. */
internal object ShellIntegration {
    const val OLD_CONFIG = "# Waddle interactive shell settings\nPS1='waddle $ '\n"
    const val DEFAULT_CONFIG = "# Waddle interactive shell settings\nPS1='\\w \\$ '\n"
    const val RUN_KEYS = "\u001b[9876~\u001b[9877~"
    fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    fun prepareConfig(file: File) {
        if (!file.exists() || (file.length() == OLD_CONFIG.toByteArray().size.toLong() && file.readText() == OLD_CONFIG)) {
            file.writeText(DEFAULT_CONFIG)
        }
    }

    fun agentRc(directory: File): String = """
        codex() (
            local mode=ask
            case "§{1-}" in
                --waddle-chat) mode=chat; shift ;;
                --waddle-terminal) mode=terminal; shift ;;
                ?*) command codex "§@"; return ;;
            esac
            if [[ §mode == terminal ]]; then command codex "§@"; return; fi
            local request=${quote(directory.path)}/agent-§BASHPID-§RANDOM
            trap 'command rm -f "§request" "§request.reply"' EXIT
            trap 'exit 130' INT TERM
            builtin printf '%s\n%s\n' "§mode" "§PWD" > "§request"
            local answer count=0
            while [[ ! -s "§request.reply" ]]; do
                ((count+=1))
                if ((count > 3000)); then builtin printf '界面选择已超时，请重试。\n'; return 1; fi
                command sleep 0.1
            done
            IFS= builtin read -r answer < "§request.reply"
            case "§answer" in
                terminal) command codex "§@" ;;
                chat) builtin printf 'Codex 对话已打开 · 可在 Waddle 中返回对话。\n' ;;
                *) return 1 ;;
            esac
        )
        export -f codex
    """.trimIndent().replace('§', '$') + "\n"

    fun rc(config: File, directory: File, nonce: String): String = """
        builtin source ${quote(config.path)}
        waddle-open() {
            if [[ §# != 1 ]]; then builtin printf '用法: waddle-open "文件路径"\n' >&2; return 2; fi
            local target=§1
            [[ §target == /* ]] || target="§PWD/§target"
            builtin printf '%s' "§target" > ${quote(directory.path)}/open-§BASHPID-§RANDOM
        }
        export -f waddle-open
        __waddle_epoch=0
        __waddle_capture_status() {
            __waddle_status=§?
            return "§__waddle_status"
        }
        __waddle_prompt() {
            local previous=§__waddle_status
            ((__waddle_epoch+=1))
            if [[ -o emacs || -o vi ]]; then
                builtin printf '\033]777;waddle;$nonce;prompt;%s;%s\007' "§__waddle_epoch" "§previous"
            else
                builtin printf '\033]777;waddle;$nonce;unavailable\007'
            fi
            return "§previous"
        }
        # Keep existing prompt hooks, including array-valued PROMPT_COMMAND.
        PROMPT_COMMAND=(__waddle_capture_status "§{PROMPT_COMMAND[@]}" __waddle_prompt)
        PS0='\e]777;waddle;$nonce;running\a'"§{PS0-}"
        PS2='\[\e]777;waddle;$nonce;continuation\a\]'"§{PS2-> }"
        __waddle_command() {
            local expected_epoch expected_cwd command
            if { IFS= builtin read -r expected_epoch && IFS= builtin read -r expected_cwd && IFS= builtin read -r command; } < ${quote(File(directory, "pending").path)} 2>/dev/null &&
                [[ "§expected_epoch" == "§__waddle_epoch" && "§expected_cwd" == "§(builtin pwd -P)" ]]; then
                READLINE_LINE=§command
                READLINE_POINT=§{#READLINE_LINE}
            else
                READLINE_LINE=''
                READLINE_POINT=0
                builtin printf '\033]777;waddle;$nonce;rejected\007'
            fi
        }
        for __waddle_map in emacs-standard vi-insert vi-command; do
            builtin bind -m "§__waddle_map" -x '"\e[9876~":__waddle_command'
            builtin bind -m "§__waddle_map" '"\e[9877~":accept-line'
        done
        unset __waddle_map
    """.trimIndent().replace('§', '$') + "\n"
}

data class TerminalContext(val sessionId: String, val sessionLabel: String, val cwd: String, val epoch: Long, val transcript: String, val exitCode: Int? = null)
