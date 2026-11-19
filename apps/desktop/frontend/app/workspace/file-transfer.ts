// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { TabRpcClient } from "@/app/store/wshrpcutil";
import { arrayToBase64, base64ToArray } from "@/util/util";
import { atom } from "jotai";
import { globalStore } from "@/app/store/jotaiStore";

const DefaultChunkSize = 1024 * 1024;
const TransferClearDelayMs = 2500;

export type FileTransferDirection = "upload" | "download";
export type FileTransferStatus = "running" | "done" | "error" | "cancelled";

export type FileTransferEntry = {
    id: string;
    direction: FileTransferDirection;
    fileName: string;
    sourcePath: string;
    destinationPath: string;
    totalBytes: number;
    transferredBytes: number;
    percent: number;
    speedBytesPerSecond: number;
    etaSeconds: number;
    status: FileTransferStatus;
    error?: string;
    startedAt: number;
    updatedAt: number;
    clearAt: number;
};

type TransferRpc = {
    FileWriteCommand?: (client: unknown, data: FileData, opts?: RpcOpts) => Promise<void>;
    FileAppendCommand?: (client: unknown, data: FileData, opts?: RpcOpts) => Promise<void>;
    FileReadCommand?: (client: unknown, data: FileData, opts?: RpcOpts) => Promise<FileData>;
    FileInfoCommand?: (client: unknown, data: FileData, opts?: RpcOpts) => Promise<FileInfo>;
    FileDeleteCommand?: (client: unknown, data: CommandDeleteFileData, opts?: RpcOpts) => Promise<void>;
};

type TransferElectronApi = Pick<ElectronApi, "showSaveFileDialog" | "writeLocalFileChunk" | "deleteLocalPath">;

type UploadFileChunksOpts = {
    client: unknown;
    rpc: TransferRpc;
    file: File;
    destinationPath: string;
    chunkSize?: number;
    signal?: AbortSignal;
    onProgress?: (transferredBytes: number) => void;
};

type DownloadFileChunksOpts = {
    client: unknown;
    rpc: TransferRpc;
    electron: Pick<TransferElectronApi, "writeLocalFileChunk">;
    sourcePath: string;
    localPath: string;
    chunkSize?: number;
    signal?: AbortSignal;
    onProgress?: (transferredBytes: number) => void;
};

type StartUploadOpts = {
    rpc: TransferRpc;
    file: File;
    destinationDir: string;
    formatRemotePath: (path: string) => Promise<string>;
    onComplete?: () => void;
    onError?: (error: Error) => void;
};

type StartDownloadOpts = {
    rpc: TransferRpc;
    electron: TransferElectronApi;
    sourcePath: string;
    fileInfo: FileInfo;
    onComplete?: () => void;
    onError?: (error: Error) => void;
};

function makeTransferId(): string {
    return globalThis.crypto?.randomUUID?.() ?? `transfer-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function joinRemotePath(dir: string, name: string): string {
    if (dir == null || dir === "" || dir === "/") {
        return `/${name}`;
    }
    return `${dir.replace(/\/+$/, "")}/${name}`;
}

function throwIfAborted(signal?: AbortSignal) {
    if (!signal?.aborted) {
        return;
    }
    throw new DOMException("Transfer cancelled", "AbortError");
}

function isAbortError(error: unknown): boolean {
    return error instanceof DOMException && error.name === "AbortError";
}

function formatBytes(bytes: number): string {
    const kib = 1024;
    const mib = kib * 1024;
    const gib = mib * 1024;
    if (!Number.isFinite(bytes) || bytes < kib) {
        return `${Math.max(0, Math.round(bytes || 0))} B`;
    }
    if (bytes < mib) {
        return `${(bytes / kib).toFixed(1)} KiB`;
    }
    if (bytes < gib) {
        return `${(bytes / mib).toFixed(1)} MiB`;
    }
    return `${(bytes / gib).toFixed(1)} GiB`;
}

function formatEta(seconds: number): string {
    if (!Number.isFinite(seconds) || seconds <= 0) {
        return "";
    }
    if (seconds < 60) {
        return `${seconds}s`;
    }
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) {
        return `${minutes}m ${remainingSeconds}s`;
    }
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m`;
}

export function makeTransferEntry({
    direction,
    fileName,
    sourcePath,
    destinationPath,
    totalBytes,
    now = Date.now(),
}: {
    direction: FileTransferDirection;
    fileName: string;
    sourcePath: string;
    destinationPath: string;
    totalBytes: number;
    now?: number;
}): FileTransferEntry {
    return {
        id: makeTransferId(),
        direction,
        fileName,
        sourcePath,
        destinationPath,
        totalBytes: Math.max(0, totalBytes ?? 0),
        transferredBytes: 0,
        percent: totalBytes > 0 ? 0 : 100,
        speedBytesPerSecond: 0,
        etaSeconds: 0,
        status: "running",
        startedAt: now,
        updatedAt: now,
        clearAt: 0,
    };
}

export function applyTransferProgress(
    transfer: FileTransferEntry,
    transferredBytes: number,
    now = Date.now()
): FileTransferEntry {
    const elapsedSeconds = Math.max(0, (now - transfer.startedAt) / 1000);
    const boundedTransferred = Math.max(0, Math.min(transferredBytes, transfer.totalBytes || transferredBytes));
    const speedBytesPerSecond = elapsedSeconds > 0 ? Math.round(boundedTransferred / elapsedSeconds) : 0;
    const remainingBytes = Math.max(0, (transfer.totalBytes ?? 0) - boundedTransferred);
    const etaSeconds = speedBytesPerSecond > 0 ? Math.ceil(remainingBytes / speedBytesPerSecond) : 0;
    const percent = transfer.totalBytes > 0 ? Math.round((boundedTransferred / transfer.totalBytes) * 100) : 100;
    return {
        ...transfer,
        transferredBytes: boundedTransferred,
        percent: Math.max(0, Math.min(100, percent)),
        speedBytesPerSecond,
        etaSeconds,
        updatedAt: now,
    };
}

export function formatTransferDetail(transfer: FileTransferEntry): string {
    const eta = formatEta(transfer.etaSeconds);
    const etaSuffix = eta === "" || transfer.status !== "running" ? "" : ` · ETA ${eta}`;
    return `${transfer.percent}% · ${formatBytes(transfer.transferredBytes)} / ${formatBytes(transfer.totalBytes)} · ${formatBytes(
        transfer.speedBytesPerSecond
    )}/s${etaSuffix}`;
}

export async function uploadFileChunks({
    client,
    rpc,
    file,
    destinationPath,
    chunkSize = DefaultChunkSize,
    signal,
    onProgress,
}: UploadFileChunksOpts): Promise<void> {
    if (rpc.FileWriteCommand == null || rpc.FileAppendCommand == null) {
        throw new Error("file write RPCs are unavailable");
    }
    if (file.size === 0) {
        throwIfAborted(signal);
        await rpc.FileWriteCommand(client, { info: { path: destinationPath }, data64: "" }, null);
        onProgress?.(0);
        return;
    }
    for (let offset = 0; offset < file.size; offset += chunkSize) {
        throwIfAborted(signal);
        const end = Math.min(offset + chunkSize, file.size);
        const bytes = new Uint8Array(await file.slice(offset, end).arrayBuffer());
        const data: FileData = {
            info: {
                path: destinationPath,
            },
            data64: arrayToBase64(bytes),
        };
        if (offset === 0) {
            await rpc.FileWriteCommand(client, data, null);
        } else {
            await rpc.FileAppendCommand(client, data, null);
        }
        throwIfAborted(signal);
        onProgress?.(end);
    }
}

export async function downloadFileChunks({
    client,
    rpc,
    electron,
    sourcePath,
    localPath,
    chunkSize = DefaultChunkSize,
    signal,
    onProgress,
}: DownloadFileChunksOpts): Promise<void> {
    if (rpc.FileInfoCommand == null || rpc.FileReadCommand == null) {
        throw new Error("file read RPCs are unavailable");
    }
    const fileInfo = await rpc.FileInfoCommand(client, { info: { path: sourcePath } }, null);
    const totalBytes = Math.max(0, fileInfo?.size ?? 0);
    if (totalBytes === 0) {
        throwIfAborted(signal);
        await electron.writeLocalFileChunk({ path: localPath, data64: "", offset: 0, truncate: true });
        onProgress?.(0);
        return;
    }
    for (let offset = 0; offset < totalBytes; offset += chunkSize) {
        throwIfAborted(signal);
        const size = Math.min(chunkSize, totalBytes - offset);
        const fileData = await rpc.FileReadCommand(
            client,
            {
                info: { path: sourcePath },
                at: { offset, size },
            },
            null
        );
        const data64 = fileData?.data64 ?? "";
        const writtenBytes = base64ToArray(data64).length;
        if (writtenBytes === 0 && size > 0) {
            throw new Error(`empty file chunk at offset ${offset}`);
        }
        await electron.writeLocalFileChunk({
            path: localPath,
            data64,
            offset,
            truncate: offset === 0,
        });
        throwIfAborted(signal);
        onProgress?.(Math.min(totalBytes, offset + writtenBytes));
    }
}

export class FileTransferModel {
    static instance: FileTransferModel = null;

    transfersAtom = atom<FileTransferEntry[]>([]);
    abortControllers = new Map<string, AbortController>();

    static getInstance(): FileTransferModel {
        if (FileTransferModel.instance == null) {
            FileTransferModel.instance = new FileTransferModel();
        }
        return FileTransferModel.instance;
    }

    setTransfer(id: string, updater: (transfer: FileTransferEntry) => FileTransferEntry) {
        globalStore.set(this.transfersAtom, (current) =>
            current.map((transfer) => (transfer.id === id ? updater(transfer) : transfer))
        );
    }

    addTransfer(transfer: FileTransferEntry) {
        globalStore.set(this.transfersAtom, (current) => [transfer, ...current]);
    }

    markTransferDone(id: string) {
        this.abortControllers.delete(id);
        this.setTransfer(id, (transfer) => ({
            ...applyTransferProgress(transfer, transfer.totalBytes),
            status: "done",
            percent: 100,
            clearAt: Date.now() + TransferClearDelayMs,
        }));
    }

    markTransferError(id: string, error: unknown, status: FileTransferStatus = "error") {
        this.abortControllers.delete(id);
        this.setTransfer(id, (transfer) => ({
            ...transfer,
            status,
            error: error instanceof Error ? error.message : `${error}`,
            updatedAt: Date.now(),
            clearAt: Date.now() + TransferClearDelayMs,
        }));
    }

    updateTransferProgress(id: string, transferredBytes: number) {
        this.setTransfer(id, (transfer) => applyTransferProgress(transfer, transferredBytes));
    }

    cancelTransfer(id: string) {
        this.abortControllers.get(id)?.abort();
        this.markTransferError(id, new DOMException("Transfer cancelled", "AbortError"), "cancelled");
    }

    clearExpiredTransfers(now = Date.now()) {
        globalStore.set(this.transfersAtom, (current) =>
            current.filter((transfer) => transfer.clearAt === 0 || transfer.clearAt > now)
        );
    }

    async startUploadFile({
        rpc,
        file,
        destinationDir,
        formatRemotePath,
        onComplete,
        onError,
    }: StartUploadOpts): Promise<string> {
        const destinationPath = joinRemotePath(destinationDir, file.name);
        const remoteDestinationPath = await formatRemotePath(destinationPath);
        const transfer = makeTransferEntry({
            direction: "upload",
            fileName: file.name,
            sourcePath: file.name,
            destinationPath,
            totalBytes: file.size,
        });
        const controller = new AbortController();
        this.abortControllers.set(transfer.id, controller);
        this.addTransfer(transfer);
        uploadFileChunks({
            client: TabRpcClient,
            rpc,
            file,
            destinationPath: remoteDestinationPath,
            signal: controller.signal,
            onProgress: (bytes) => this.updateTransferProgress(transfer.id, bytes),
        })
            .then(() => {
                this.markTransferDone(transfer.id);
                onComplete?.();
            })
            .catch((error) => {
                if (isAbortError(error)) {
                    rpc.FileDeleteCommand?.(TabRpcClient, { path: remoteDestinationPath, recursive: false }, null).catch(() => {});
                    this.markTransferError(transfer.id, error, "cancelled");
                    return;
                }
                this.markTransferError(transfer.id, error);
                onError?.(error instanceof Error ? error : new Error(`${error}`));
            });
        return transfer.id;
    }

    async startDownloadFile({ rpc, electron, sourcePath, fileInfo, onComplete, onError }: StartDownloadOpts): Promise<string> {
        const fileName = fileInfo?.name ?? sourcePath.split("/").pop() ?? "download";
        const localPath = await electron.showSaveFileDialog(fileName);
        if (localPath == null || localPath === "") {
            return null;
        }
        const transfer = makeTransferEntry({
            direction: "download",
            fileName,
            sourcePath,
            destinationPath: localPath,
            totalBytes: fileInfo?.size ?? 0,
        });
        const controller = new AbortController();
        this.abortControllers.set(transfer.id, controller);
        this.addTransfer(transfer);
        downloadFileChunks({
            client: TabRpcClient,
            rpc,
            electron,
            sourcePath,
            localPath,
            signal: controller.signal,
            onProgress: (bytes) => this.updateTransferProgress(transfer.id, bytes),
        })
            .then(() => {
                this.markTransferDone(transfer.id);
                onComplete?.();
            })
            .catch((error) => {
                if (isAbortError(error)) {
                    electron.deleteLocalPath(localPath).catch(() => {});
                    this.markTransferError(transfer.id, error, "cancelled");
                    return;
                }
                this.markTransferError(transfer.id, error);
                onError?.(error instanceof Error ? error : new Error(`${error}`));
            });
        return transfer.id;
    }
}
