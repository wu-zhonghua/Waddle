// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { SettingsKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type PreviewEnv = WaddleEnvSubset<{
    electron: {
        onQuicklook: WaddleEnv["electron"]["onQuicklook"];
        openNativePath: WaddleEnv["electron"]["openNativePath"];
        showSaveFileDialog: WaddleEnv["electron"]["showSaveFileDialog"];
        writeLocalFileChunk: WaddleEnv["electron"]["writeLocalFileChunk"];
        deleteLocalPath: WaddleEnv["electron"]["deleteLocalPath"];
    };
    rpc: {
        ConnEnsureCommand: WaddleEnv["rpc"]["ConnEnsureCommand"];
        FileInfoCommand: WaddleEnv["rpc"]["FileInfoCommand"];
        FileReadCommand: WaddleEnv["rpc"]["FileReadCommand"];
        FileListStreamCommand: WaddleEnv["rpc"]["FileListStreamCommand"];
        FileWriteCommand: WaddleEnv["rpc"]["FileWriteCommand"];
        FileAppendCommand: WaddleEnv["rpc"]["FileAppendCommand"];
        FileMoveCommand: WaddleEnv["rpc"]["FileMoveCommand"];
        FileDeleteCommand: WaddleEnv["rpc"]["FileDeleteCommand"];
        SetConfigCommand: WaddleEnv["rpc"]["SetConfigCommand"];
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
        FetchSuggestionsCommand: WaddleEnv["rpc"]["FetchSuggestionsCommand"];
        DisposeSuggestionsCommand: WaddleEnv["rpc"]["DisposeSuggestionsCommand"];
        FileCopyCommand: WaddleEnv["rpc"]["FileCopyCommand"];
        FileCreateCommand: WaddleEnv["rpc"]["FileCreateCommand"];
        FileMkdirCommand: WaddleEnv["rpc"]["FileMkdirCommand"];
    };
    atoms: {
        fullConfigAtom: WaddleEnv["atoms"]["fullConfigAtom"];
    };
    services: {
        object: WaddleEnv["services"]["object"];
    };
    wos: WaddleEnv["wos"];
    getSettingsKeyAtom: SettingsKeyAtomFnType<
        "preview:showhiddenfiles" | "editor:fontsize" | "preview:defaultsort" | "preview:directoryviewmode"
    >;
    getConnStatusAtom: WaddleEnv["getConnStatusAtom"];
}>;
