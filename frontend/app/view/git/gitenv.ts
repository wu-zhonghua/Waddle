// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { MetaKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type GitEnv = WaddleEnvSubset<{
    rpc: {
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
        ConnConnectCommand: WaddleEnv["rpc"]["ConnConnectCommand"];
        ConnDisconnectCommand: WaddleEnv["rpc"]["ConnDisconnectCommand"];
        ConnReinstallWshCommand: WaddleEnv["rpc"]["ConnReinstallWshCommand"];
        WaitForRouteCommand: WaddleEnv["rpc"]["WaitForRouteCommand"];
        WaddleAIAddContextCommand: WaddleEnv["rpc"]["WaddleAIAddContextCommand"];
        GitStatusCommand: WaddleEnv["rpc"]["GitStatusCommand"];
        GitDiffCommand: WaddleEnv["rpc"]["GitDiffCommand"];
        GitReviewDiffCommand: WaddleEnv["rpc"]["GitReviewDiffCommand"];
        GitStageCommand: WaddleEnv["rpc"]["GitStageCommand"];
        GitUnstageCommand: WaddleEnv["rpc"]["GitUnstageCommand"];
        GitCommitCommand: WaddleEnv["rpc"]["GitCommitCommand"];
    };
    getConnStatusAtom: WaddleEnv["getConnStatusAtom"];
    getBlockMetaKeyAtom: MetaKeyAtomFnType<"connection" | "cmd:cwd">;
}>;
