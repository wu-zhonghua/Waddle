// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { getDefaultPlotMeta, type DataItem } from "@/app/view/sysinfo/sysinfo";
import { getSysinfoStatusMetricIds, makeSysinfoStatusReadings } from "./sysinfo-status";

describe("workspace sysinfo status bar helpers", () => {
    it("keeps GPU utilization and GPU memory as separate status metrics", () => {
        const item: DataItem = {
            ts: 2000,
            cpu: 12,
            "mem:used": 21,
            "gpu:util": 42,
            "gpu:memused": 10,
            "gpu:memtotal": 80,
            "net:download": 1.5,
            "net:upload": 0.25,
        };

        expect(getSysinfoStatusMetricIds(item)).toEqual([
            "cpu",
            "mem:used",
            "gpu:util",
            "gpu:memused",
            "net:download",
            "net:upload",
        ]);
    });

    it("formats missing GPU values as stable placeholders", () => {
        const meta = getDefaultPlotMeta();
        const item: DataItem = {
            ts: 2000,
            cpu: 12,
            "mem:used": 21,
        };

        const readings = makeSysinfoStatusReadings(item, null, meta);

        expect(readings.map((reading) => reading.metric)).toEqual(["cpu", "mem:used", "gpu:util", "gpu:memused"]);
        expect(readings.find((reading) => reading.metric == "gpu:util")).toMatchObject({
            name: "GPU",
            value: "--",
            unit: "%",
        });
        expect(readings.find((reading) => reading.metric == "gpu:memused")).toMatchObject({
            name: "GPU Memory",
            value: "--",
            unit: "GB",
        });
    });

    it("formats GPU memory as used over total GB", () => {
        const meta = getDefaultPlotMeta();
        const item: DataItem = {
            ts: 2000,
            cpu: 12,
            "mem:used": 21,
            "gpu:util": 42,
            "gpu:memused": 10,
            "gpu:memtotal": 80,
        };

        const readings = makeSysinfoStatusReadings(item, null, meta);

        expect(readings.find((reading) => reading.metric == "gpu:memused")).toMatchObject({
            name: "GPU Memory",
            value: "10.0/80.0",
            unit: "GB",
        });
    });
});
