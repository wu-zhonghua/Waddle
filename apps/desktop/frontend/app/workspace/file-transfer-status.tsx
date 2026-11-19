// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { cn } from "@/util/util";
import { useAtomValue } from "jotai";
import * as React from "react";
import { FileTransferEntry, FileTransferModel, formatTransferDetail } from "./file-transfer";

function getTransferLabel(transfer: FileTransferEntry): string {
    if (transfer.status === "done") {
        return transfer.direction === "upload" ? "Uploaded" : "Downloaded";
    }
    if (transfer.status === "cancelled") {
        return "Cancelled";
    }
    if (transfer.status === "error") {
        return "Transfer failed";
    }
    return transfer.direction === "upload" ? "Uploading" : "Downloading";
}

function getTransferIcon(transfer: FileTransferEntry): string {
    if (transfer.status === "error") {
        return "fa-triangle-exclamation";
    }
    if (transfer.status === "cancelled") {
        return "fa-ban";
    }
    if (transfer.status === "done") {
        return "fa-check";
    }
    return transfer.direction === "upload" ? "fa-arrow-up" : "fa-arrow-down";
}

function getTransferDetail(transfer: FileTransferEntry): string {
    if (transfer.status === "error" && transfer.error != null) {
        return transfer.error;
    }
    return formatTransferDetail(transfer);
}

function WorkspaceFileTransferStatus() {
    const model = FileTransferModel.getInstance();
    const transfers = useAtomValue(model.transfersAtom);
    const visibleTransfers = transfers.filter((transfer) => transfer.status === "running" || transfer.clearAt > 0).slice(0, 3);

    React.useEffect(() => {
        const nextClearAt = transfers
            .map((transfer) => transfer.clearAt)
            .filter((clearAt) => clearAt > Date.now())
            .sort((left, right) => left - right)[0];
        if (nextClearAt == null) {
            return;
        }
        const timeout = window.setTimeout(() => model.clearExpiredTransfers(), Math.max(0, nextClearAt - Date.now()));
        return () => window.clearTimeout(timeout);
    }, [model, transfers]);

    if (visibleTransfers.length === 0) {
        return null;
    }

    return (
        <div className="shrink-0 border-t border-border bg-background/95 text-[11px] text-primary shadow-[0_-2px_10px_rgba(0,0,0,0.18)] backdrop-blur-sm">
            {visibleTransfers.map((transfer) => (
                <div key={transfer.id} className="relative h-7 overflow-hidden">
                    <div className="absolute inset-x-0 bottom-0 h-0.5 bg-foreground/10">
                        <div
                            className={cn(
                                "h-full transition-[width] duration-200",
                                transfer.status === "error"
                                    ? "bg-red-400"
                                    : transfer.status === "cancelled"
                                      ? "bg-zinc-400"
                                      : "bg-accent"
                            )}
                            style={{ width: `${transfer.percent}%` }}
                        />
                    </div>
                    <div className="flex h-full min-w-0 items-center gap-2 px-3">
                        <i className={cn("fa-solid fa-fw", getTransferIcon(transfer))} />
                        <span className="font-medium">{getTransferLabel(transfer)}</span>
                        <span className="min-w-0 truncate text-secondary">{transfer.fileName}</span>
                        <span className="ml-auto whitespace-nowrap tabular-nums text-secondary">{getTransferDetail(transfer)}</span>
                        {transfer.status === "running" && (
                            <button
                                type="button"
                                className="flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center rounded hover:bg-hoverbg"
                                aria-label={`Cancel ${transfer.fileName} transfer`}
                                onClick={() => model.cancelTransfer(transfer.id)}
                            >
                                <i className="fa-solid fa-xmark" />
                            </button>
                        )}
                    </div>
                </div>
            ))}
        </div>
    );
}

export { WorkspaceFileTransferStatus };
