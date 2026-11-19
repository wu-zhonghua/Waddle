// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    getDefaultPlotMeta,
    getPlotMetrics,
    getSysinfoDisplayMode,
    makeMetricReading,
    type DataItem,
} from "./sysinfo";

describe("sysinfo plot helpers", () => {
    const sample: DataItem = {
        ts: 1000,
        cpu: 42,
        "cpu:0": 44,
        "mem:used": 12,
        "disk:usedpct": 67,
        "net:download": 5.5,
        "net:upload": 1.25,
        "load:1": 2.4,
        "proc:count": 321,
        "temp:max": 71,
        "gpu:util": 33,
        "gpu:memused": 10,
        "gpu:memtotal": 80,
    };

    it("builds a dashboard from available system metrics", () => {
        expect(getPlotMetrics("Dashboard", sample)).toEqual([
            "cpu",
            "mem:used",
            "disk:usedpct",
            "net:download",
            "net:upload",
            "load:1",
            "proc:count",
            "temp:max",
            "gpu:util",
            "gpu:memused",
        ]);
    });

    it("omits unavailable GPU metrics instead of plotting fake data", () => {
        expect(getPlotMetrics("GPU", { ts: 1000 })).toEqual([]);
    });

    it("exposes dedicated disk and network plot types", () => {
        expect(getPlotMetrics("Disk", sample)).toEqual(["disk:usedpct"]);
        expect(getPlotMetrics("Network", sample)).toEqual(["net:download", "net:upload"]);
    });

    it("defines labels and ranges for new monitor metrics", () => {
        const meta = getDefaultPlotMeta();

        expect(meta.get("disk:usedpct")).toMatchObject({ name: "Disk Used", label: "%", maxy: 100 });
        expect(meta.get("net:download")).toMatchObject({ name: "Download", label: "MB/s", miny: 0 });
        expect(meta.get("gpu:util")).toMatchObject({ name: "GPU", label: "%", maxy: 100 });
    });

    it("uses numeric cards for the default dashboard and plots for focused views", () => {
        expect(getSysinfoDisplayMode("Dashboard")).toBe("summary");
        expect(getSysinfoDisplayMode("GPU")).toBe("plots");
        expect(getSysinfoDisplayMode("CPU + Mem")).toBe("plots");
    });

    it("formats live metric readings with units and deltas", () => {
        const meta = getDefaultPlotMeta();
        const previous: DataItem = {
            ts: 1000,
            cpu: 40,
            "net:download": 6,
            "gpu:memused": 22,
            "gpu:memtotal": 80,
        };
        const latest: DataItem = {
            ts: 2000,
            cpu: 42,
            "net:download": 5.5,
            "gpu:memused": 24,
            "gpu:memtotal": 80,
        };

        expect(makeMetricReading("cpu", latest, previous, meta.get("cpu"))).toMatchObject({
            name: "CPU %",
            value: "42",
            unit: "%",
            deltaLabel: "+2",
        });
        expect(makeMetricReading("net:download", latest, previous, meta.get("net:download"))).toMatchObject({
            name: "Download",
            value: "5.50",
            unit: "MB/s",
            deltaLabel: "-0.50",
        });
        expect(makeMetricReading("gpu:memused", latest, previous, meta.get("gpu:memused"))).toMatchObject({
            name: "GPU Memory",
            value: "24.0/80.0",
            unit: "GB",
            deltaLabel: "+2.0",
        });
    });
});
