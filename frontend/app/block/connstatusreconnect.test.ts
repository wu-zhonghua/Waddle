// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { makeWshReconnect } from "./connstatusreconnect";

describe("wsh reconnect", () => {
    it("reinstalls wsh before disconnecting and reconnecting", async () => {
        const calls: string[] = [];
        const reconnect = makeWshReconnect({
            reinstall: async () => {
                calls.push("reinstall");
            },
            disconnect: async () => {
                calls.push("disconnect");
            },
            connect: async () => {
                calls.push("connect");
            },
        });

        await reconnect();

        expect(calls).toEqual(["reinstall", "disconnect", "connect"]);
    });

    it("keeps the current connection when reinstalling fails", async () => {
        const disconnect = vi.fn();
        const connect = vi.fn();
        const reconnect = makeWshReconnect({
            reinstall: async () => {
                throw new Error("no space left on device");
            },
            disconnect,
            connect,
        });

        await expect(reconnect()).rejects.toThrow("no space left on device");
        expect(disconnect).not.toHaveBeenCalled();
        expect(connect).not.toHaveBeenCalled();
    });

    it("connects when disconnect reports the connection is already down", async () => {
        const connect = vi.fn();
        const reconnect = makeWshReconnect({
            reinstall: async () => undefined,
            disconnect: async () => {
                throw new Error("connection not found");
            },
            connect,
        });

        await reconnect();

        expect(connect).toHaveBeenCalledOnce();
    });

    it("shares one recovery attempt between concurrent calls", async () => {
        let releaseReinstall: () => void;
        const reinstall = vi.fn(
            () =>
                new Promise<void>((resolve) => {
                    releaseReinstall = resolve;
                })
        );
        const reconnect = makeWshReconnect({
            reinstall,
            disconnect: async () => undefined,
            connect: async () => undefined,
        });

        const first = reconnect();
        const second = reconnect();
        releaseReinstall!();
        await Promise.all([first, second]);

        expect(reinstall).toHaveBeenCalledOnce();
    });
});
