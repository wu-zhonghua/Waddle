// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { changeBlockConnection, ConnectionChangeTimeoutMs } from "./conntypeahead-utils";

describe("changeBlockConnection", () => {
    it("ensures remote connections before updating block metadata", async () => {
        const calls: string[] = [];
        const rpc = {
            ConnEnsureCommand: vi.fn(async (_client, data, opts) => {
                calls.push(`ensure:${data.connname}:${opts.timeout}`);
            }),
            SetMetaCommand: vi.fn(async (_client, data) => {
                calls.push(`set:${data.meta.connection}:${data.meta.file}:${data.meta["cmd:cwd"]}`);
            }),
        };

        await changeBlockConnection({
            rpc,
            rpcClient: {},
            blockId: "block-1",
            blockORef: "block:block-1",
            blockMeta: { connection: "", file: "/old/path" },
            connName: "root@ace_4gpu:5562",
        });

        expect(calls).toEqual([`ensure:root@ace_4gpu:5562:${ConnectionChangeTimeoutMs}`, "set:root@ace_4gpu:5562:~:null"]);
    });

    it("does not update block metadata when remote connection ensure fails", async () => {
        const rpc = {
            ConnEnsureCommand: vi.fn(async () => {
                throw new Error("connect failed");
            }),
            SetMetaCommand: vi.fn(async () => {}),
        };

        await expect(
            changeBlockConnection({
                rpc,
                rpcClient: {},
                blockId: "block-1",
                blockORef: "block:block-1",
                blockMeta: { connection: "", file: "" },
                connName: "root@ace_4gpu:5562",
            })
        ).rejects.toThrow("connect failed");

        expect(rpc.SetMetaCommand).not.toHaveBeenCalled();
    });

    it("updates metadata directly when switching to local", async () => {
        const rpc = {
            ConnEnsureCommand: vi.fn(async () => {}),
            SetMetaCommand: vi.fn(async () => {}),
        };

        await changeBlockConnection({
            rpc,
            rpcClient: {},
            blockId: "block-1",
            blockORef: "block:block-1",
            blockMeta: { connection: "root@ace_4gpu:5562", file: "~" },
            connName: "",
        });

        expect(rpc.ConnEnsureCommand).not.toHaveBeenCalled();
        expect(rpc.SetMetaCommand).toHaveBeenCalledWith(
            {},
            {
                oref: "block:block-1",
                meta: { connection: null, file: "~", "cmd:cwd": null },
            }
        );
    });
});
