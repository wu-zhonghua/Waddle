// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { isLocalConnName } from "@/util/util";

export const ConnectionChangeTimeoutMs = 60 * 60 * 1000;

export function formatSshConnectionLabel(connName: string): string {
    const atIndex = connName.lastIndexOf("@");
    const hostAndPort = atIndex >= 0 ? connName.slice(atIndex + 1) : connName;
    const hostName = hostAndPort.split(":")[0];
    if (hostName == "" || hostName == connName) {
        return connName;
    }
    return `${hostName} (${connName})`;
}

export type ChangeBlockConnectionRpc = {
    ConnEnsureCommand: (
        client: unknown,
        data: { connname: string; logblockid: string },
        opts?: { timeout?: number }
    ) => Promise<void>;
    SetMetaCommand: (client: unknown, data: { oref: string; meta: MetaType }) => Promise<void>;
};

export async function changeBlockConnection({
    rpc,
    rpcClient,
    blockId,
    blockORef,
    blockMeta,
    connName,
}: {
    rpc: ChangeBlockConnectionRpc;
    rpcClient: unknown;
    blockId: string;
    blockORef: string;
    blockMeta: MetaType;
    connName: string;
}): Promise<void> {
    const nextConnName = connName == "" ? null : connName;
    if (nextConnName == blockMeta?.connection) {
        return;
    }
    if (!isLocalConnName(nextConnName)) {
        await rpc.ConnEnsureCommand(
            rpcClient,
            { connname: nextConnName, logblockid: blockId },
            { timeout: ConnectionChangeTimeoutMs }
        );
    }

    const oldFile = blockMeta?.file ?? "";
    const newFile = oldFile == "" ? "" : "~";
    await rpc.SetMetaCommand(rpcClient, {
        oref: blockORef,
        meta: { connection: nextConnName, file: newFile, "cmd:cwd": null },
    });
}
