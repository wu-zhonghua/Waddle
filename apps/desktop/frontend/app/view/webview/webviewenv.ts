// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { MetaKeyAtomFnType, SettingsKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type WebViewEnv = WaddleEnvSubset<{
    electron: {
        openExternal: WaddleEnv["electron"]["openExternal"];
        getWebviewPreload: WaddleEnv["electron"]["getWebviewPreload"];
        clearWebviewStorage: WaddleEnv["electron"]["clearWebviewStorage"];
        getConfigDir: WaddleEnv["electron"]["getConfigDir"];
        setWebviewFocus: WaddleEnv["electron"]["setWebviewFocus"];
    };
    rpc: {
        FetchSuggestionsCommand: WaddleEnv["rpc"]["FetchSuggestionsCommand"];
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
        SetConfigCommand: WaddleEnv["rpc"]["SetConfigCommand"];
    };
    wos: WaddleEnv["wos"];
    createBlock: WaddleEnv["createBlock"];
    getSettingsKeyAtom: SettingsKeyAtomFnType<"web:defaulturl" | "web:defaultsearch">;
    getBlockMetaKeyAtom: MetaKeyAtomFnType<
        "web:hidenav" | "web:useragenttype" | "web:zoom" | "web:partition"
    >;
}>;
