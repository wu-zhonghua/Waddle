// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    applyWshInstallProgressEvent,
    clearExpiredWshInstallProgress,
    getProgressDetail,
    makeInitialWshInstallProgressState,
} from "./wsh-install-progress";

describe("wsh install progress state", () => {
    it("shows running progress without scheduling a clear", () => {
        const state = applyWshInstallProgressEvent(
            makeInitialWshInstallProgressState(),
            {
                connname: "root@ace:5561",
                status: "running",
                percent: 25,
                written: 25,
                total: 100,
            },
            1000
        );

        expect(state.current?.connname).toBe("root@ace:5561");
        expect(state.current?.percent).toBe(25);
        expect(state.startedat).toBe(1000);
        expect(state.clearat).toBe(0);
    });

    it("keeps done status briefly and then clears it", () => {
        const doneState = applyWshInstallProgressEvent(
            makeInitialWshInstallProgressState(),
            {
                connname: "root@ace:5561",
                status: "done",
                percent: 100,
                written: 100,
                total: 100,
            },
            1000
        );

        expect(doneState.current?.status).toBe("done");
        expect(doneState.clearat).toBeGreaterThan(1000);
        expect(clearExpiredWshInstallProgress(doneState, doneState.clearat - 1).current?.status).toBe("done");
        expect(clearExpiredWshInstallProgress(doneState, doneState.clearat).current).toBeNull();
    });

    it("formats ETA from elapsed progress", () => {
        const state = applyWshInstallProgressEvent(
            makeInitialWshInstallProgressState(),
            {
                connname: "root@ace:5561",
                status: "running",
                percent: 25,
                written: 25,
                total: 100,
            },
            1000
        );

        expect(getProgressDetail(state.current, state.startedat, 5000)).toBe("25% (25 B / 100 B) · ETA 12s");
    });
});
