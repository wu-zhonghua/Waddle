// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { arrayToBase64, base64ToArray } from "@/util/util";
import { describe, expect, it, vi } from "vitest";
import {
    applyTransferProgress,
    downloadFileChunks,
    formatTransferDetail,
    makeTransferEntry,
    uploadFileChunks,
} from "./file-transfer";

function bytesFromCall(call: any[]): number[] {
    return Array.from(base64ToArray(call[1].data64));
}

describe("file transfer helpers", () => {
    it("uploads files in chunks with an initial truncate followed by appends", async () => {
        const file = new File([new Uint8Array([1, 2, 3, 4, 5])], "sample.bin");
        const progress: number[] = [];
        const rpc = {
            FileWriteCommand: vi.fn().mockResolvedValue(undefined),
            FileAppendCommand: vi.fn().mockResolvedValue(undefined),
        };

        await uploadFileChunks({
            client: null,
            rpc,
            file,
            destinationPath: "/tmp/sample.bin",
            chunkSize: 2,
            signal: new AbortController().signal,
            onProgress: (transferredBytes) => progress.push(transferredBytes),
        });

        expect(rpc.FileWriteCommand).toHaveBeenCalledTimes(1);
        expect(rpc.FileAppendCommand).toHaveBeenCalledTimes(2);
        expect(rpc.FileWriteCommand.mock.calls[0][1]).toMatchObject({
            info: { path: "/tmp/sample.bin" },
        });
        expect(bytesFromCall(rpc.FileWriteCommand.mock.calls[0])).toEqual([1, 2]);
        expect(bytesFromCall(rpc.FileAppendCommand.mock.calls[0])).toEqual([3, 4]);
        expect(bytesFromCall(rpc.FileAppendCommand.mock.calls[1])).toEqual([5]);
        expect(progress).toEqual([2, 4, 5]);
    });

    it("downloads files in chunks using ranged reads and local chunk writes", async () => {
        const bytes = new Uint8Array([9, 8, 7, 6, 5]);
        const progress: number[] = [];
        const rpc = {
            FileInfoCommand: vi.fn().mockResolvedValue({ path: "ssh://root@host/tmp/remote.bin", size: bytes.length, name: "remote.bin" }),
            FileReadCommand: vi.fn(async (_client: unknown, data: FileData) => {
                const offset = data.at?.offset ?? 0;
                const size = data.at?.size ?? bytes.length;
                return {
                    info: { path: "ssh://root@host/tmp/remote.bin", size: bytes.length, name: "remote.bin" },
                    data64: arrayToBase64(bytes.slice(offset, offset + size)),
                };
            }),
        };
        const electron = {
            writeLocalFileChunk: vi.fn().mockResolvedValue(undefined),
        };

        await downloadFileChunks({
            client: null,
            rpc,
            electron,
            sourcePath: "ssh://root@host/tmp/remote.bin",
            localPath: "/Users/me/Downloads/remote.bin",
            chunkSize: 2,
            signal: new AbortController().signal,
            onProgress: (transferredBytes) => progress.push(transferredBytes),
        });

        expect(rpc.FileReadCommand.mock.calls.map((call) => call[1].at)).toEqual([
            { offset: 0, size: 2 },
            { offset: 2, size: 2 },
            { offset: 4, size: 1 },
        ]);
        expect(electron.writeLocalFileChunk.mock.calls.map((call) => call[0].truncate)).toEqual([true, false, false]);
        expect(electron.writeLocalFileChunk.mock.calls.map((call) => call[0].offset)).toEqual([0, 2, 4]);
        expect(electron.writeLocalFileChunk.mock.calls.map((call) => Array.from(base64ToArray(call[0].data64)))).toEqual([
            [9, 8],
            [7, 6],
            [5],
        ]);
        expect(progress).toEqual([2, 4, 5]);
    });

    it("formats transfer progress with speed and ETA", () => {
        const transfer = makeTransferEntry({
            direction: "download",
            fileName: "weights.bin",
            sourcePath: "ssh://host/weights.bin",
            destinationPath: "/Users/me/weights.bin",
            totalBytes: 1024,
            now: 1000,
        });
        const updated = applyTransferProgress(transfer, 512, 3000);

        expect(updated.percent).toBe(50);
        expect(updated.speedBytesPerSecond).toBe(256);
        expect(updated.etaSeconds).toBe(2);
        expect(formatTransferDetail(updated)).toBe("50% · 512 B / 1.0 KiB · 256 B/s · ETA 2s");
    });
});
