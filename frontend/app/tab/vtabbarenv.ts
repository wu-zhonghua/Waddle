// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { SettingsKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";

export type VTabBarEnv = WaddleEnvSubset<{
    electron: {
        createTab: WaddleEnv["electron"]["createTab"];
        closeTab: WaddleEnv["electron"]["closeTab"];
        setActiveTab: WaddleEnv["electron"]["setActiveTab"];
        deleteWorkspace: WaddleEnv["electron"]["deleteWorkspace"];
        createWorkspace: WaddleEnv["electron"]["createWorkspace"];
        switchWorkspace: WaddleEnv["electron"]["switchWorkspace"];
        installAppUpdate: WaddleEnv["electron"]["installAppUpdate"];
    };
    rpc: {
        UpdateWorkspaceTabIdsCommand: WaddleEnv["rpc"]["UpdateWorkspaceTabIdsCommand"];
        UpdateTabNameCommand: WaddleEnv["rpc"]["UpdateTabNameCommand"];
        ActivityCommand: WaddleEnv["rpc"]["ActivityCommand"];
        SetConfigCommand: WaddleEnv["rpc"]["SetConfigCommand"];
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
    };
    atoms: {
        staticTabId: WaddleEnv["atoms"]["staticTabId"];
        fullConfigAtom: WaddleEnv["atoms"]["fullConfigAtom"];
        reinitVersion: WaddleEnv["atoms"]["reinitVersion"];
        documentHasFocus: WaddleEnv["atoms"]["documentHasFocus"];
        workspace: WaddleEnv["atoms"]["workspace"];
        updaterStatusAtom: WaddleEnv["atoms"]["updaterStatusAtom"];
        isFullScreen: WaddleEnv["atoms"]["isFullScreen"];
    };
    services: {
        workspace: WaddleEnv["services"]["workspace"];
    };
    wos: WaddleEnv["wos"];
    showContextMenu: WaddleEnv["showContextMenu"];
    getSettingsKeyAtom: SettingsKeyAtomFnType<"tab:confirmclose" | "app:tabbar" | "app:hideaibutton">;
    mockSetWaddleObj: WaddleEnv["mockSetWaddleObj"];
    isWindows: WaddleEnv["isWindows"];
    isMacOS: WaddleEnv["isMacOS"];
}>;
