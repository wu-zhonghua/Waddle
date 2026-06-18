// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { MetaKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type WaddleConfigEnv = WaddleEnvSubset<{
    electron: {
        getConfigDir: WaddleEnv["electron"]["getConfigDir"];
        getPlatform: WaddleEnv["electron"]["getPlatform"];
    };
    rpc: {
        FileInfoCommand: WaddleEnv["rpc"]["FileInfoCommand"];
        FileReadCommand: WaddleEnv["rpc"]["FileReadCommand"];
        FileWriteCommand: WaddleEnv["rpc"]["FileWriteCommand"];
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
        GetSecretsLinuxStorageBackendCommand: WaddleEnv["rpc"]["GetSecretsLinuxStorageBackendCommand"];
        GetSecretsNamesCommand: WaddleEnv["rpc"]["GetSecretsNamesCommand"];
        GetSecretsCommand: WaddleEnv["rpc"]["GetSecretsCommand"];
        SetSecretsCommand: WaddleEnv["rpc"]["SetSecretsCommand"];
        RecordTEventCommand: WaddleEnv["rpc"]["RecordTEventCommand"];
    };
    atoms: {
        fullConfigAtom: WaddleEnv["atoms"]["fullConfigAtom"];
    };
    getBlockMetaKeyAtom: MetaKeyAtomFnType<"file">;
    isWindows: WaddleEnv["isWindows"];
}>;
