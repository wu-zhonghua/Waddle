// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import {
    ConnConfigKeyAtomFnType,
    MetaKeyAtomFnType,
    SettingsKeyAtomFnType,
    WaddleEnv,
    WaddleEnvSubset,
} from "@/app/waveenv/waveenv";

export type BlockEnv = WaddleEnvSubset<{
    getSettingsKeyAtom: SettingsKeyAtomFnType<
        | "app:focusfollowscursor"
        | "app:showoverlayblocknums"
        | "term:showsplitbuttons"
        | "window:magnifiedblockblurprimarypx"
        | "window:magnifiedblockopacity"
    >;
    showContextMenu: WaddleEnv["showContextMenu"];
    atoms: {
        modalOpen: WaddleEnv["atoms"]["modalOpen"];
        controlShiftDelayAtom: WaddleEnv["atoms"]["controlShiftDelayAtom"];
    };
    electron: {
        openExternal: WaddleEnv["electron"]["openExternal"];
    };
    rpc: {
        ActivityCommand: WaddleEnv["rpc"]["ActivityCommand"];
        ConnEnsureCommand: WaddleEnv["rpc"]["ConnEnsureCommand"];
        ConnDisconnectCommand: WaddleEnv["rpc"]["ConnDisconnectCommand"];
        ConnConnectCommand: WaddleEnv["rpc"]["ConnConnectCommand"];
        SetConnectionsConfigCommand: WaddleEnv["rpc"]["SetConnectionsConfigCommand"];
        DismissWshFailCommand: WaddleEnv["rpc"]["DismissWshFailCommand"];
    };
    wos: WaddleEnv["wos"];
    getConnStatusAtom: WaddleEnv["getConnStatusAtom"];
    getLocalHostDisplayNameAtom: WaddleEnv["getLocalHostDisplayNameAtom"];
    getConnConfigKeyAtom: ConnConfigKeyAtomFnType<"conn:wshenabled">;
    getBlockMetaKeyAtom: MetaKeyAtomFnType<
        | "frame:text"
        | "frame:activebordercolor"
        | "frame:bordercolor"
        | "view"
        | "connection"
        | "icon:color"
        | "frame:title"
        | "frame:icon"
    >;
    getTabMetaKeyAtom: MetaKeyAtomFnType<"bg:activebordercolor" | "bg:bordercolor" | "tab:background">;
    getConfigBackgroundAtom: WaddleEnv["getConfigBackgroundAtom"];
}>;
