// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { SettingsKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type TabBarEnv = WaddleEnvSubset<{
    electron: {
        createTab: WaddleEnv["electron"]["createTab"];
        closeTab: WaddleEnv["electron"]["closeTab"];
        setActiveTab: WaddleEnv["electron"]["setActiveTab"];
        showWorkspaceAppMenu: WaddleEnv["electron"]["showWorkspaceAppMenu"];
        installAppUpdate: WaddleEnv["electron"]["installAppUpdate"];
    };
    rpc: {
        ActivityCommand: WaddleEnv["rpc"]["ActivityCommand"];
        SetConfigCommand: WaddleEnv["rpc"]["SetConfigCommand"];
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
        UpdateTabNameCommand: WaddleEnv["rpc"]["UpdateTabNameCommand"];
        UpdateWorkspaceTabIdsCommand: WaddleEnv["rpc"]["UpdateWorkspaceTabIdsCommand"];
    };
    atoms: {
        fullConfigAtom: WaddleEnv["atoms"]["fullConfigAtom"];
        hasConfigErrors: WaddleEnv["atoms"]["hasConfigErrors"];
        staticTabId: WaddleEnv["atoms"]["staticTabId"];
        isFullScreen: WaddleEnv["atoms"]["isFullScreen"];
        zoomFactorAtom: WaddleEnv["atoms"]["zoomFactorAtom"];
        reinitVersion: WaddleEnv["atoms"]["reinitVersion"];
        updaterStatusAtom: WaddleEnv["atoms"]["updaterStatusAtom"];
    };
    wos: WaddleEnv["wos"];
    getSettingsKeyAtom: SettingsKeyAtomFnType<"app:hideaibutton" | "app:tabbar" | "tab:confirmclose" | "window:showmenubar">;
    showContextMenu: WaddleEnv["showContextMenu"];
    mockSetWaddleObj: WaddleEnv["mockSetWaddleObj"];
    isWindows: WaddleEnv["isWindows"];
    isMacOS: WaddleEnv["isMacOS"];
}>;
