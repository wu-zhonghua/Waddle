// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { MetaKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type GitDiffEnv = WaddleEnvSubset<{
    rpc: {
        GitFileDiffCommand: WaddleEnv["rpc"]["GitFileDiffCommand"];
    };
    getBlockMetaKeyAtom: MetaKeyAtomFnType<"connection" | "cmd:cwd" | "git:path" | "git:origpath" | "git:staged">;
}>;
