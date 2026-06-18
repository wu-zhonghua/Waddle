// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { ContextMenuModel } from "@/app/store/contextmenu";
import {
    atoms,
    createBlock,
    getBlockMetaKeyAtom,
    getConfigBackgroundAtom,
    getConnConfigKeyAtom,
    getConnStatusAtom,
    getLocalHostDisplayNameAtom,
    getSettingsKeyAtom,
    getTabMetaKeyAtom,
    isDev,
    WOS,
} from "@/app/store/global";
import { AllServiceImpls } from "@/app/store/services";
import { RpcApi } from "@/app/store/wshclientapi";
import { WaddleEnv } from "@/app/waveenv/waveenv";
import { isMacOS, isWindows, PLATFORM } from "@/util/platformutil";

export function makeWaddleEnvImpl(): WaddleEnv {
    return {
        isMock: false,
        electron: (window as any).api,
        rpc: RpcApi,
        getSettingsKeyAtom,
        platform: PLATFORM,
        isDev,
        isWindows,
        isMacOS,
        atoms,
        createBlock,
        services: AllServiceImpls,
        callBackendService: WOS.callBackendService,
        showContextMenu: (menu: ContextMenuItem[], e: React.MouseEvent) => {
            ContextMenuModel.getInstance().showContextMenu(menu, e);
        },
        getConnStatusAtom,
        getLocalHostDisplayNameAtom,
        wos: {
            getWaddleObjectAtom: WOS.getWaddleObjectAtom,
            getWaddleObjectLoadingAtom: WOS.getWaddleObjectLoadingAtom,
            isWaddleObjectNullAtom: WOS.isWaddleObjectNullAtom,
            useWaddleObjectValue: WOS.useWaddleObjectValue,
        },
        getBlockMetaKeyAtom,
        getTabMetaKeyAtom,
        getConfigBackgroundAtom,
        getConnConfigKeyAtom,

        mockSetWaddleObj: <T extends WaddleObj>(_oref: string, _obj: T) => {
            throw new Error("mockSetWaddleObj is only available in the preview server");
        },
        mockModels: new Map<any, any>(),
    };
}
