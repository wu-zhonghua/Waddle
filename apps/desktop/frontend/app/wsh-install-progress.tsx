// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { waveEventSubscribeSingle } from "@/app/store/wps";
import { cn } from "@/util/util";
import * as React from "react";

const ClearDelayMs = 1800;

type WshInstallProgressState = {
    current: WshInstallProgressData;
    clearat: number;
    startedat: number;
};

function makeInitialWshInstallProgressState(): WshInstallProgressState {
    return { current: null, clearat: 0, startedat: 0 };
}

function applyWshInstallProgressEvent(
    state: WshInstallProgressState,
    progress: WshInstallProgressData,
    now = Date.now()
): WshInstallProgressState {
    const clearat = progress.status === "running" ? 0 : now + ClearDelayMs;
    const sameRunningInstall = state.current?.connname === progress.connname && state.current?.status === "running";
    const startedat = sameRunningInstall && state.startedat > 0 ? state.startedat : now;
    return { ...state, current: progress, clearat, startedat };
}

function clearExpiredWshInstallProgress(
    state: WshInstallProgressState,
    now = Date.now()
): WshInstallProgressState {
    if (state.current == null || state.clearat === 0 || now < state.clearat) {
        return state;
    }
    return makeInitialWshInstallProgressState();
}

function formatInstallByteCount(size: number): string {
    const kib = 1024;
    const mib = kib * 1024;
    if (size < kib) {
        return `${size} B`;
    }
    if (size < mib) {
        return `${(size / kib).toFixed(1)} KiB`;
    }
    return `${(size / mib).toFixed(1)} MiB`;
}

function getProgressPercent(progress: WshInstallProgressData): number {
    if (progress.percent == null) {
        return 0;
    }
    return Math.max(0, Math.min(100, progress.percent));
}

function getProgressStatusLabel(progress: WshInstallProgressData): string {
    if (progress.status === "done") {
        return "wsh installed";
    }
    if (progress.status === "error") {
        return "wsh install failed";
    }
    return "Installing wsh";
}

function formatEtaSeconds(seconds: number): string {
    if (seconds < 60) {
        return `${seconds}s`;
    }
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) {
        return `${minutes}m ${remainingSeconds}s`;
    }
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return `${hours}h ${remainingMinutes}m`;
}

function getProgressEta(progress: WshInstallProgressData, startedat: number, now: number): string {
    const percent = getProgressPercent(progress);
    if (progress.status !== "running" || percent <= 0 || percent >= 100 || startedat <= 0 || now <= startedat) {
        return "";
    }
    const elapsedSeconds = (now - startedat) / 1000;
    const totalSeconds = elapsedSeconds / (percent / 100);
    const remainingSeconds = Math.max(0, Math.ceil(totalSeconds - elapsedSeconds));
    return formatEtaSeconds(remainingSeconds);
}

function getProgressDetail(progress: WshInstallProgressData, startedat = 0, now = Date.now()): string {
    const total = progress.total ?? 0;
    const eta = getProgressEta(progress, startedat, now);
    const etaSuffix = eta === "" ? "" : ` · ETA ${eta}`;
    if (total <= 0) {
        return `${getProgressPercent(progress)}%${etaSuffix}`;
    }
    return `${getProgressPercent(progress)}% (${formatInstallByteCount(progress.written ?? 0)} / ${formatInstallByteCount(total)})${etaSuffix}`;
}

function WshInstallProgressBar() {
    const [state, setState] = React.useState<WshInstallProgressState>(makeInitialWshInstallProgressState);

    React.useEffect(() => {
        return waveEventSubscribeSingle({
            eventType: "remote:wshinstallprogress",
            handler: (event) => {
                if (event.data == null) {
                    return;
                }
                setState((curState) => applyWshInstallProgressEvent(curState, event.data));
            },
        });
    }, []);

    React.useEffect(() => {
        if (state.clearat === 0) {
            return;
        }
        const timeoutMs = Math.max(0, state.clearat - Date.now());
        const timeoutId = window.setTimeout(() => {
            setState((curState) => clearExpiredWshInstallProgress(curState));
        }, timeoutMs);
        return () => window.clearTimeout(timeoutId);
    }, [state.clearat]);

    const progress = state.current;
    if (progress == null) {
        return null;
    }

    const percent = getProgressPercent(progress);
    const isError = progress.status === "error";
    const isDone = progress.status === "done";

    return (
        <div
            className={cn(
                "fixed inset-x-0 bottom-0 z-[1000] h-6 border-t border-border bg-background/95 text-[11px] text-primary shadow-[0_-2px_10px_rgba(0,0,0,0.18)] backdrop-blur-sm",
                isError ? "text-red-300" : "text-primary"
            )}
        >
            <div className="absolute inset-x-0 bottom-0 h-0.5 bg-foreground/10">
                <div
                    className={cn("h-full transition-[width] duration-200", isError ? "bg-red-400" : "bg-accent")}
                    style={{ width: `${percent}%` }}
                />
            </div>
            <div className="flex h-full items-center gap-2 px-3">
                <span className="font-medium">{getProgressStatusLabel(progress)}</span>
                <span className="min-w-0 truncate text-secondary">{progress.connname}</span>
                <span className={cn("ml-auto tabular-nums", isDone ? "text-green-300" : "text-secondary")}>
                    {getProgressDetail(progress, state.startedat)}
                </span>
            </div>
        </div>
    );
}

export {
    WshInstallProgressBar,
    applyWshInstallProgressEvent,
    clearExpiredWshInstallProgress,
    getProgressDetail,
    makeInitialWshInstallProgressState,
};
