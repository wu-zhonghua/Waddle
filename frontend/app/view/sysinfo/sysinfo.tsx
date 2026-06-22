// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { globalStore } from "@/app/store/jotaiStore";
import { makeORef } from "@/app/store/wos";
import * as util from "@/util/util";
import * as Plot from "@observablehq/plot";
import clsx from "clsx";
import dayjs from "dayjs";
import * as htl from "htl";
import * as jotai from "jotai";
import * as React from "react";

import { useDimensionsWithExistingRef } from "@/app/hook/useDimensions";
import { Tooltip } from "@/app/element/tooltip";
import { waveEventSubscribeSingle } from "@/app/store/wps";
import { TabRpcClient } from "@/app/store/wshrpcutil";
import type { MetaKeyAtomFnType, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";
import { OverlayScrollbarsComponent, OverlayScrollbarsComponentRef } from "overlayscrollbars-react";

export type SysinfoEnv = WaddleEnvSubset<{
    rpc: {
        EventReadHistoryCommand: WaddleEnv["rpc"]["EventReadHistoryCommand"];
        SetMetaCommand: WaddleEnv["rpc"]["SetMetaCommand"];
    };
    atoms: {
        fullConfigAtom: WaddleEnv["atoms"]["fullConfigAtom"];
    };
    getConnStatusAtom: WaddleEnv["getConnStatusAtom"];
    getBlockMetaKeyAtom: MetaKeyAtomFnType<"graph:numpoints" | "sysinfo:type" | "connection" | "count">;
}>;

const DefaultNumPoints = 120;

export type DataItem = {
    ts: number;
    [k: string]: number;
};

function defaultCpuMeta(name: string): TimeSeriesMeta {
    return {
        name: name,
        label: "%",
        miny: 0,
        maxy: 100,
        color: "var(--sysinfo-cpu-color)",
        decimalPlaces: 0,
    };
}

function defaultMemMeta(name: string, maxY: string): TimeSeriesMeta {
    return {
        name: name,
        label: "GB",
        miny: 0,
        maxy: maxY,
        color: "var(--sysinfo-mem-color)",
        decimalPlaces: 1,
    };
}

function defaultPercentMeta(name: string, color: string): TimeSeriesMeta {
    return {
        name,
        label: "%",
        miny: 0,
        maxy: 100,
        color,
        decimalPlaces: 0,
    };
}

function defaultValueMeta(name: string, label: string, color: string, decimalPlaces = 1): TimeSeriesMeta {
    return {
        name,
        label,
        miny: 0,
        color,
        decimalPlaces,
    };
}

function filterAvailableMetrics(metrics: string[], dataItem: DataItem): string[] {
    return metrics.filter((metric) => dataItem?.[metric] != null);
}

const DashboardMetrics = [
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
];

const PlotTypes: Record<string, (dataItem: DataItem) => Array<string>> = {
    Dashboard: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(DashboardMetrics, dataItem);
    },
    CPU: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["cpu"], dataItem);
    },
    GPU: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["gpu:util", "gpu:memused"], dataItem);
    },
    Disk: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["disk:usedpct"], dataItem);
    },
    Network: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["net:download", "net:upload"], dataItem);
    },
    Memory: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["mem:used", "mem:available"], dataItem);
    },
    Load: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["load:1", "load:5", "load:15"], dataItem);
    },
    Processes: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["proc:count"], dataItem);
    },
    Temperature: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["temp:max"], dataItem);
    },
    Uptime: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["uptime:hours"], dataItem);
    },
    Mem: function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["mem:used"], dataItem);
    },
    "CPU + Mem": function (dataItem: DataItem): Array<string> {
        return filterAvailableMetrics(["cpu", "mem:used"], dataItem);
    },
    "All CPU": function (dataItem: DataItem): Array<string> {
        return Object.keys(dataItem)
            .filter((item) => item.startsWith("cpu") && item != "cpu")
            .sort((a, b) => {
                const valA = parseInt(a.replace("cpu:", ""));
                const valB = parseInt(b.replace("cpu:", ""));
                return valA - valB;
            });
    },
};

export type SysinfoDisplayMode = "summary" | "plots";

export type MetricReading = {
    metric: string;
    name: string;
    value: string;
    unit: string;
    deltaLabel: string;
    deltaDirection: "up" | "down" | "flat" | "unknown";
    color: string;
};

export function getSysinfoDisplayMode(plotType: string): SysinfoDisplayMode {
    if (plotType == "Dashboard") {
        return "summary";
    }
    return "plots";
}

export function getPlotMetrics(plotType: string, dataItem: DataItem): string[] {
    const plotFn = PlotTypes[plotType] ?? PlotTypes.Dashboard;
    return plotFn(dataItem);
}

const DefaultPlotMeta: Record<string, TimeSeriesMeta> = {
    cpu: defaultCpuMeta("CPU %"),
    "mem:total": defaultMemMeta("Memory Total", "mem:total"),
    "mem:used": defaultMemMeta("Memory Used", "mem:total"),
    "mem:free": defaultMemMeta("Memory Free", "mem:total"),
    "mem:available": defaultMemMeta("Memory Available", "mem:total"),
    "disk:usedpct": defaultPercentMeta("Disk Used", "var(--term-yellow)"),
    "disk:used": defaultMemMeta("Disk Used", "disk:total"),
    "disk:free": defaultMemMeta("Disk Free", "disk:total"),
    "disk:total": defaultMemMeta("Disk Total", "disk:total"),
    "net:download": defaultValueMeta("Download", "MB/s", "var(--term-bright-green)", 2),
    "net:upload": defaultValueMeta("Upload", "MB/s", "var(--term-bright-blue)", 2),
    "load:1": defaultValueMeta("Load 1m", "load", "var(--term-bright-magenta)", 2),
    "load:5": defaultValueMeta("Load 5m", "load", "var(--term-magenta)", 2),
    "load:15": defaultValueMeta("Load 15m", "load", "var(--term-bright-cyan)", 2),
    "proc:count": defaultValueMeta("Processes", "proc", "var(--term-bright-cyan)", 0),
    "temp:max": defaultValueMeta("Temperature", "C", "var(--term-bright-red)", 1),
    "uptime:hours": defaultValueMeta("Uptime", "h", "var(--term-green)", 1),
    "gpu:util": defaultPercentMeta("GPU", "var(--term-bright-yellow)"),
    "gpu:memused": defaultMemMeta("GPU Memory", "gpu:memtotal"),
    "gpu:memtotal": defaultMemMeta("GPU Memory Total", "gpu:memtotal"),
};
for (let i = 0; i < 32; i++) {
    DefaultPlotMeta[`cpu:${i}`] = defaultCpuMeta(`Core ${i}`);
}

export function getDefaultPlotMeta(): Map<string, TimeSeriesMeta> {
    return new Map(Object.entries(DefaultPlotMeta));
}

function formatMetricNumber(value: number, decimalPlaces: number): string {
    if (!Number.isFinite(value)) {
        return "--";
    }
    return value.toFixed(decimalPlaces);
}

function formatMetricValue(metric: string, dataItem: DataItem, decimalPlaces: number): string {
    const value = dataItem?.[metric];
    if (metric != "gpu:memused") {
        return formatMetricNumber(value, decimalPlaces);
    }
    const totalValue = dataItem?.["gpu:memtotal"];
    if (!Number.isFinite(totalValue)) {
        return formatMetricNumber(value, decimalPlaces);
    }
    return `${formatMetricNumber(value, decimalPlaces)}/${formatMetricNumber(totalValue, decimalPlaces)}`;
}

function makeDeltaLabel(delta: number, decimalPlaces: number): string {
    if (!Number.isFinite(delta)) {
        return "";
    }
    const roundedDelta = Number(delta.toFixed(decimalPlaces));
    const formattedDelta = Math.abs(roundedDelta).toFixed(decimalPlaces);
    if (roundedDelta > 0) {
        return `+${formattedDelta}`;
    }
    if (roundedDelta < 0) {
        return `-${formattedDelta}`;
    }
    return decimalPlaces == 0 ? "0" : Number(0).toFixed(decimalPlaces);
}

export function makeMetricReading(
    metric: string,
    dataItem: DataItem,
    previousDataItem: DataItem,
    yvalMeta: TimeSeriesMeta
): MetricReading {
    const decimalPlaces = yvalMeta?.decimalPlaces ?? 0;
    const currentValue = dataItem?.[metric];
    const previousValue = previousDataItem?.[metric];
    const delta = currentValue - previousValue;
    let deltaDirection: MetricReading["deltaDirection"] = "unknown";
    if (Number.isFinite(delta)) {
        if (delta > 0) {
            deltaDirection = "up";
        } else if (delta < 0) {
            deltaDirection = "down";
        } else {
            deltaDirection = "flat";
        }
    }

    return {
        metric,
        name: yvalMeta?.name ?? metric,
        value: formatMetricValue(metric, dataItem, decimalPlaces),
        unit: yvalMeta?.label ?? "",
        deltaLabel: makeDeltaLabel(delta, decimalPlaces),
        deltaDirection,
        color: yvalMeta?.color ?? "var(--accent-color)",
    };
}

export function convertWaddleEventToDataItem(event: Extract<WaddleEvent, { event: "sysinfo" }>): DataItem {
    const eventData = event.data;
    if (eventData == null || eventData.ts == null || eventData.values == null) {
        return null;
    }
    const dataItem = { ts: eventData.ts };
    for (const key in eventData.values) {
        dataItem[key] = eventData.values[key];
    }
    return dataItem;
}

class SysinfoViewModel implements ViewModel {
    viewType: string;
    termMode: jotai.Atom<string>;
    htmlElemFocusRef: React.RefObject<HTMLInputElement>;
    blockId: string;
    viewIcon: jotai.Atom<string>;
    viewText: jotai.Atom<string>;
    viewName: jotai.Atom<string>;
    dataAtom: jotai.PrimitiveAtom<Array<DataItem>>;
    addInitialDataAtom: jotai.WritableAtom<unknown, [DataItem[]], void>;
    addContinuousDataAtom: jotai.WritableAtom<unknown, [DataItem], void>;
    incrementCount: jotai.WritableAtom<unknown, [], Promise<void>>;
    loadingAtom: jotai.PrimitiveAtom<boolean>;
    numPoints: jotai.Atom<number>;
    metrics: jotai.Atom<string[]>;
    connection: jotai.Atom<string>;
    manageConnection: jotai.Atom<boolean>;
    filterOutNowsh: jotai.Atom<boolean>;
    connStatus: jotai.Atom<ConnStatus>;
    plotMetaAtom: jotai.PrimitiveAtom<Map<string, TimeSeriesMeta>>;
    endIconButtons: jotai.Atom<IconButtonDecl[]>;
    plotTypeSelectedAtom: jotai.Atom<string>;
    env: SysinfoEnv;

    constructor({ blockId, waveEnv }: ViewModelInitType) {
        this.viewType = "sysinfo";
        this.blockId = blockId;
        this.env = waveEnv;
        this.addInitialDataAtom = jotai.atom(null, (get, set, points) => {
            const targetLen = get(this.numPoints) + 1;
            try {
                const newDataRaw = [...points];
                if (newDataRaw.length == 0) {
                    return;
                }
                const latestItemTs = newDataRaw[newDataRaw.length - 1]?.ts ?? 0;
                const cutoffTs = latestItemTs - 1000 * targetLen;
                const blankItemTemplate = { ...newDataRaw[newDataRaw.length - 1] };
                for (const key in blankItemTemplate) {
                    blankItemTemplate[key] = NaN;
                }

                const newDataFiltered = newDataRaw.filter((dataItem) => dataItem.ts >= cutoffTs);
                if (newDataFiltered.length == 0) {
                    return;
                }
                const newDataWithGaps: Array<DataItem> = [];
                if (newDataFiltered[0].ts > cutoffTs) {
                    const blankItemStart = { ...blankItemTemplate, ts: cutoffTs };
                    const blankItemEnd = { ...blankItemTemplate, ts: newDataFiltered[0].ts - 1 };
                    newDataWithGaps.push(blankItemStart);
                    newDataWithGaps.push(blankItemEnd);
                }
                newDataWithGaps.push(newDataFiltered[0]);
                for (let i = 1; i < newDataFiltered.length; i++) {
                    const prevIdxItem = newDataFiltered[i - 1];
                    const curIdxItem = newDataFiltered[i];
                    const timeDiff = curIdxItem.ts - prevIdxItem.ts;
                    if (timeDiff > 2000) {
                        const blankItemStart = { ...blankItemTemplate, ts: prevIdxItem.ts + 1, blank: 1 };
                        const blankItemEnd = { ...blankItemTemplate, ts: curIdxItem.ts - 1, blank: 1 };
                        newDataWithGaps.push(blankItemStart);
                        newDataWithGaps.push(blankItemEnd);
                    }
                    newDataWithGaps.push(curIdxItem);
                }
                set(this.dataAtom, newDataWithGaps);
            } catch (e) {
                console.log("Error adding data to sysinfo", e);
            }
        });
        this.addContinuousDataAtom = jotai.atom(null, (get, set, newPoint) => {
            const targetLen = get(this.numPoints) + 1;
            const data = get(this.dataAtom);
            try {
                const latestItemTs = newPoint?.ts ?? 0;
                const cutoffTs = latestItemTs - 1000 * targetLen;
                data.push(newPoint);
                const newData = data.filter((dataItem) => dataItem.ts >= cutoffTs);
                set(this.dataAtom, newData);
            } catch (e) {
                console.log("Error adding data to sysinfo", e);
            }
        });
        this.plotMetaAtom = jotai.atom(getDefaultPlotMeta());
        this.manageConnection = jotai.atom(true);
        this.filterOutNowsh = jotai.atom(true);
        this.loadingAtom = jotai.atom(true);
        this.numPoints = jotai.atom((get) => {
            const metaNumPoints = get(this.env.getBlockMetaKeyAtom(blockId, "graph:numpoints"));
            if (metaNumPoints == null || metaNumPoints <= 0) {
                return DefaultNumPoints;
            }
            return metaNumPoints;
        });
        this.metrics = jotai.atom((get) => {
            const plotType = get(this.plotTypeSelectedAtom);
            const plotData = get(this.dataAtom);
            try {
                const metrics = getPlotMetrics(plotType, plotData[plotData.length - 1]);
                if (metrics == null || !Array.isArray(metrics)) {
                    return ["cpu"];
                }
                return metrics;
            } catch (e) {
                return ["cpu"];
            }
        });
        this.plotTypeSelectedAtom = jotai.atom((get) => {
            const plotType = get(this.env.getBlockMetaKeyAtom(blockId, "sysinfo:type"));
            if (plotType == null || typeof plotType != "string") {
                return "Dashboard";
            }
            return plotType;
        });
        this.viewIcon = jotai.atom((get) => {
            return "chart-line"; // should not be hardcoded
        });
        this.viewName = jotai.atom((get) => {
            return get(this.plotTypeSelectedAtom);
        });
        this.incrementCount = jotai.atom(null, async (get, _set) => {
            const count = get(this.env.getBlockMetaKeyAtom(blockId, "count")) ?? 0;
            await this.env.rpc.SetMetaCommand(TabRpcClient, {
                oref: makeORef("block", this.blockId),
                meta: { count: count + 1 },
            });
        });
        this.connection = jotai.atom((get) => {
            const connValue = get(this.env.getBlockMetaKeyAtom(blockId, "connection"));
            if (util.isBlank(connValue)) {
                return "local";
            }
            return connValue;
        });
        this.dataAtom = jotai.atom([]);
        this.loadInitialData();
        this.connStatus = jotai.atom((get) => {
            const connName = get(this.env.getBlockMetaKeyAtom(blockId, "connection"));
            const connAtom = this.env.getConnStatusAtom(connName);
            return get(connAtom);
        });
    }

    get viewComponent(): ViewComponent {
        return SysinfoView;
    }

    async loadInitialData() {
        globalStore.set(this.loadingAtom, true);
        try {
            const numPoints = globalStore.get(this.numPoints);
            const connName = globalStore.get(this.connection);
            const initialData = await this.env.rpc.EventReadHistoryCommand(TabRpcClient, {
                event: "sysinfo",
                scope: connName,
                maxitems: numPoints,
            });
            if (initialData == null) {
                return;
            }
            this.getDefaultData();
            const initialDataItems: DataItem[] = initialData.map(convertWaddleEventToDataItem);
            // splice the initial data into the default data (replacing the newest points)
            //newData.splice(newData.length - initialDataItems.length, initialDataItems.length, ...initialDataItems);
            globalStore.set(this.addInitialDataAtom, initialDataItems);
        } catch (e) {
            console.log("Error loading initial data for sysinfo", e);
        } finally {
            globalStore.set(this.loadingAtom, false);
        }
    }

    getSettingsMenuItems(): ContextMenuItem[] {
        const fullConfig = globalStore.get(this.env.atoms.fullConfigAtom);
        const termThemes = fullConfig?.termthemes ?? {};
        const termThemeKeys = Object.keys(termThemes);
        const plotData = globalStore.get(this.dataAtom);

        termThemeKeys.sort((a, b) => {
            return (termThemes[a]["display:order"] ?? 0) - (termThemes[b]["display:order"] ?? 0);
        });
        const fullMenu: ContextMenuItem[] = [];
        let submenu: ContextMenuItem[];
        if (plotData.length == 0) {
            submenu = [];
        } else {
            submenu = Object.keys(PlotTypes).map((plotType) => {
                const dataTypes = getPlotMetrics(plotType, plotData[plotData.length - 1]);
                const currentlySelected = globalStore.get(this.plotTypeSelectedAtom);
                const menuItem: ContextMenuItem = {
                    label: plotType,
                    type: "radio",
                    checked: currentlySelected == plotType,
                    click: async () => {
                        await this.env.rpc.SetMetaCommand(TabRpcClient, {
                            oref: makeORef("block", this.blockId),
                            meta: { "graph:metrics": dataTypes, "sysinfo:type": plotType },
                        });
                    },
                };
                return menuItem;
            });
        }

        fullMenu.push({
            label: "Plot Type",
            submenu: submenu,
        });
        fullMenu.push({ type: "separator" });
        return fullMenu;
    }

    getDefaultData(): DataItem[] {
        // set it back one to avoid backwards line being possible
        const numPoints = globalStore.get(this.numPoints);
        const currentTime = Date.now() - 1000;
        const points: DataItem[] = [];
        for (let i = numPoints; i > -1; i--) {
            points.push({ ts: currentTime - i * 1000 });
        }
        return points;
    }
}

const _plotColors = ["#58C142", "#FFC107", "#FF5722", "#2196F3", "#9C27B0", "#00BCD4", "#FFEB3B", "#795548"];

type SysinfoViewProps = {
    blockId: string;
    model: SysinfoViewModel;
};

function resolveDomainBound(value: number | string, dataItem: DataItem): number | undefined {
    if (typeof value == "number") {
        return value;
    } else if (typeof value == "string") {
        return dataItem?.[value];
    } else {
        return undefined;
    }
}

function SysinfoView({ model, blockId }: SysinfoViewProps) {
    const connName = jotai.useAtomValue(model.connection);
    const lastConnName = React.useRef(connName);
    const connStatus = jotai.useAtomValue(model.connStatus);
    const addContinuousData = jotai.useSetAtom(model.addContinuousDataAtom);
    const loading = jotai.useAtomValue(model.loadingAtom);

    React.useEffect(() => {
        if (connStatus?.status != "connected") {
            return;
        }
        if (lastConnName.current !== connName) {
            lastConnName.current = connName;
            model.loadInitialData();
        }
    }, [connStatus.status, connName]);
    React.useEffect(() => {
        const unsubFn = waveEventSubscribeSingle({
            eventType: "sysinfo",
            scope: connName,
            handler: (event) => {
                const loading = globalStore.get(model.loadingAtom);
                if (loading) {
                    return;
                }
                const dataItem = convertWaddleEventToDataItem(event);
                const prevData = globalStore.get(model.dataAtom);
                const prevLastTs = prevData[prevData.length - 1]?.ts ?? 0;
                if (dataItem.ts - prevLastTs > 2000) {
                    model.loadInitialData();
                } else {
                    addContinuousData(dataItem);
                }
            },
        });
        console.log("subscribe to sysinfo", connName);
        return () => {
            unsubFn();
        };
    }, [connName, addContinuousData]);
    if (connStatus?.status != "connected") {
        return null;
    }
    if (loading) {
        return null;
    }
    return <SysinfoViewInner key={connStatus?.connection ?? "local"} blockId={blockId} model={model} />;
}

type SingleLinePlotProps = {
    plotData: Array<DataItem>;
    yval: string;
    yvalMeta: TimeSeriesMeta;
    blockId: string;
    defaultColor: string;
    title?: boolean;
    sparkline?: boolean;
    targetLen: number;
    className?: string;
};

function SingleLinePlot({
    plotData,
    yval,
    yvalMeta,
    blockId,
    defaultColor,
    title = false,
    sparkline = false,
    targetLen,
    className,
}: SingleLinePlotProps) {
    const containerRef = React.useRef<HTMLInputElement>(null);
    const domRect = useDimensionsWithExistingRef(containerRef, 300);
    const plotHeight = domRect?.height ?? 0;
    const plotWidth = domRect?.width ?? 0;
    const marks: Plot.Markish[] = [];
    const decimalPlaces = yvalMeta?.decimalPlaces ?? 0;
    let color = yvalMeta?.color;
    if (!color) {
        color = defaultColor;
    }
    marks.push(
        () => htl.svg`<defs>
      <linearGradient id="gradient-${blockId}-${yval}" gradientTransform="rotate(90)">
        <stop offset="0%" stop-color="${color}" stop-opacity="0.7" />
        <stop offset="100%" stop-color="${color}" stop-opacity="0" />
      </linearGradient>
	      </defs>`
    );

    marks.push(
        Plot.lineY(plotData, {
            stroke: color,
            strokeWidth: 2,
            x: "ts",
            y: yval,
        })
    );

    // only add the gradient for single items
    marks.push(
        Plot.areaY(plotData, {
            fill: `url(#gradient-${blockId}-${yval})`,
            x: "ts",
            y: yval,
        })
    );
    if (title) {
        marks.push(
            Plot.text([yvalMeta?.name], {
                frameAnchor: "top-left",
                dx: 4,
                fill: "var(--grey-text-color)",
            })
        );
    }
    const labelY = yvalMeta?.label ?? "?";
    marks.push(
        Plot.ruleX(
            plotData,
            Plot.pointerX({ x: "ts", py: yval, stroke: "var(--grey-text-color)", strokeWidth: 1, strokeDasharray: 2 })
        )
    );
    marks.push(
        Plot.ruleY(
            plotData,
            Plot.pointerX({ px: "ts", y: yval, stroke: "var(--grey-text-color)", strokeWidth: 1, strokeDasharray: 2 })
        )
    );
    marks.push(
        Plot.tip(
            plotData,
            Plot.pointerX({
                x: "ts",
                y: yval,
                fill: "var(--main-bg-color)",
                anchor: "middle",
                dy: -30,
                title: (d) =>
                    `${dayjs.unix(d.ts / 1000).format("HH:mm:ss")} ${Number(d[yval]).toFixed(decimalPlaces)}${labelY}`,
                textPadding: 3,
            })
        )
    );
    marks.push(
        Plot.dot(
            plotData,
            Plot.pointerX({ x: "ts", y: yval, fill: color, r: 3, stroke: "var(--main-text-color)", strokeWidth: 1 })
        )
    );
    const maxY = resolveDomainBound(yvalMeta?.maxy, plotData[plotData.length - 1]) ?? 100;
    const minY = resolveDomainBound(yvalMeta?.miny, plotData[plotData.length - 1]) ?? 0;
    const maxX = plotData[plotData.length - 1].ts;
    const minX = maxX - targetLen * 1000;
    const plot = Plot.plot({
        axis: !sparkline,
        x: {
            grid: true,
            label: "time",
            tickFormat: (d) => `${dayjs.unix(d / 1000).format("HH:mm:ss")}`,
            domain: [minX, maxX],
        },
        y: { label: labelY, domain: [minY, maxY] },
        width: plotWidth,
        height: plotHeight,
        marks: marks,
    });

    React.useEffect(() => {
        containerRef.current.append(plot);

        return () => {
            plot.remove();
        };
    }, [plot, plotWidth, plotHeight]);

    return <div ref={containerRef} className={clsx("min-h-[100px]", className)} />;
}

function findPreviousMetricDataItem(plotData: DataItem[], yval: string): DataItem {
    for (let i = plotData.length - 2; i >= 0; i--) {
        if (Number.isFinite(plotData[i]?.[yval])) {
            return plotData[i];
        }
    }
    return null;
}

type MetricValueProps = {
    reading: MetricReading;
};

function MetricValue({ reading }: MetricValueProps) {
    const [changing, setChanging] = React.useState(false);
    const previousValue = React.useRef(reading.value);

    React.useEffect(() => {
        if (previousValue.current == reading.value) {
            return;
        }
        previousValue.current = reading.value;
        setChanging(true);
        const timeoutId = window.setTimeout(() => {
            setChanging(false);
        }, 220);
        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [reading.value]);

    return (
        <div
            className={clsx(
                "flex min-w-0 items-baseline gap-1 font-mono tabular-nums transition-all duration-200",
                changing && "scale-[1.03] text-accent"
            )}
        >
            <span className="min-w-0 truncate text-[28px] leading-none font-semibold text-primary">{reading.value}</span>
            {reading.unit && <span className="shrink-0 text-[11px] leading-none text-secondary">{reading.unit}</span>}
        </div>
    );
}

type SysinfoMetricCardProps = {
    plotData: DataItem[];
    yval: string;
    yvalMeta: TimeSeriesMeta;
    blockId: string;
    targetLen: number;
    expanded: boolean;
    onToggle: (metric: string) => void;
};

function SysinfoMetricCard({
    plotData,
    yval,
    yvalMeta,
    blockId,
    targetLen,
    expanded,
    onToggle,
}: SysinfoMetricCardProps) {
    const latestDataItem = plotData[plotData.length - 1];
    const previousDataItem = findPreviousMetricDataItem(plotData, yval);
    const reading = makeMetricReading(yval, latestDataItem, previousDataItem, yvalMeta);
    const chartTitle = expanded ? "Hide trend" : "Show trend";
    const deltaClass = {
        up: "text-success",
        down: "text-error",
        flat: "text-secondary",
        unknown: "text-secondary",
    }[reading.deltaDirection];

    return (
        <section
            className={clsx(
                "min-w-0 rounded-[8px] border border-border bg-black/10 px-3 py-2 transition-colors",
                expanded && "border-accent/70 bg-accent/5"
            )}
        >
            <div className="mb-2 flex min-w-0 items-center justify-between gap-2">
                <div className="min-w-0 truncate text-[11px] font-medium text-secondary">{reading.name}</div>
                <Tooltip content={chartTitle} placement="left" openDelay={200}>
                    <button
                        type="button"
                        aria-label={chartTitle}
                        aria-pressed={expanded}
                        title={chartTitle}
                        onClick={() => onToggle(yval)}
                        className={clsx(
                            "flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center rounded-[6px] text-[11px] text-secondary transition-colors hover:bg-white/10 hover:text-primary",
                            expanded && "bg-accent/20 text-accent"
                        )}
                    >
                        <i className="fa-solid fa-chart-line" />
                    </button>
                </Tooltip>
            </div>
            <div className="flex min-w-0 items-end justify-between gap-2">
                <MetricValue reading={reading} />
                {reading.deltaLabel && (
                    <span className={clsx("shrink-0 font-mono text-[11px] tabular-nums", deltaClass)}>
                        {reading.deltaLabel}
                    </span>
                )}
            </div>
            {expanded && (
                <div className="mt-3 h-[78px] overflow-hidden rounded-[6px] border border-border/70 bg-black/20">
                    <SingleLinePlot
                        plotData={plotData}
                        yval={yval}
                        yvalMeta={yvalMeta}
                        blockId={blockId}
                        defaultColor={"var(--accent-color)"}
                        targetLen={targetLen}
                        sparkline
                        className="h-full min-h-0"
                    />
                </div>
            )}
        </section>
    );
}

type SysinfoMetricDashboardProps = {
    plotData: DataItem[];
    yvals: string[];
    plotMeta: Map<string, TimeSeriesMeta>;
    blockId: string;
    targetLen: number;
};

function SysinfoMetricDashboard({ plotData, yvals, plotMeta, blockId, targetLen }: SysinfoMetricDashboardProps) {
    const [expandedMetrics, setExpandedMetrics] = React.useState<Set<string>>(new Set());
    const availableMetricKey = yvals.join("|");
    const hasMetricData = plotData != null && plotData.length > 0 && yvals.length > 0;

    React.useEffect(() => {
        setExpandedMetrics((prev) => {
            const next = new Set([...prev].filter((metric) => yvals.includes(metric)));
            if (next.size == prev.size) {
                return prev;
            }
            return next;
        });
    }, [availableMetricKey]);

    const toggleMetric = React.useCallback((metric: string) => {
        setExpandedMetrics((prev) => {
            const next = new Set(prev);
            if (next.has(metric)) {
                next.delete(metric);
            } else {
                next.add(metric);
            }
            return next;
        });
    }, []);

    if (!hasMetricData) {
        return <div className="flex h-full min-h-[100px] items-center justify-center text-xs text-secondary">No data</div>;
    }

    return (
        <div className="grid w-full content-start gap-2 p-2 [grid-template-columns:repeat(auto-fit,minmax(150px,1fr))]">
            {yvals.map((yval) => {
                return (
                    <SysinfoMetricCard
                        key={`metric-${blockId}-${yval}`}
                        plotData={plotData}
                        yval={yval}
                        yvalMeta={plotMeta.get(yval)}
                        blockId={blockId}
                        targetLen={targetLen}
                        expanded={expandedMetrics.has(yval)}
                        onToggle={toggleMetric}
                    />
                );
            })}
        </div>
    );
}

const SysinfoViewInner = React.memo(({ model }: SysinfoViewProps) => {
    const plotData = jotai.useAtomValue(model.dataAtom);
    const yvals = jotai.useAtomValue(model.metrics);
    const plotMeta = jotai.useAtomValue(model.plotMetaAtom);
    const plotType = jotai.useAtomValue(model.plotTypeSelectedAtom);
    const osRef = React.useRef<OverlayScrollbarsComponentRef>(null);
    const targetLen = jotai.useAtomValue(model.numPoints) + 1;
    const displayMode = getSysinfoDisplayMode(plotType);
    let title = false;
    let cols2 = false;
    if (yvals.length > 1) {
        title = true;
    }
    if (yvals.length > 2) {
        cols2 = true;
    }
    const hasPlotData = plotData != null && plotData.length > 0 && yvals.length > 0;

    return (
        <OverlayScrollbarsComponent
            ref={osRef}
            className="flex flex-col flex-grow mb-0 overflow-y-auto"
            options={{ scrollbars: { autoHide: "leave" } }}
        >
            {displayMode == "summary" && (
                <SysinfoMetricDashboard
                    plotData={plotData}
                    yvals={yvals}
                    plotMeta={plotMeta}
                    blockId={model.blockId}
                    targetLen={targetLen}
                />
            )}
            {displayMode == "plots" && (
                <div
                    className={clsx("w-full h-full grid grid-rows-[repeat(auto-fit,minmax(100px,1fr))] gap-[10px]", {
                        "grid-cols-2": cols2,
                    })}
                >
                    {!hasPlotData && (
                        <div className="flex h-full min-h-[100px] items-center justify-center text-xs text-secondary">
                            No data
                        </div>
                    )}
                    {hasPlotData &&
                        yvals.map((yval, _idx) => {
                            return (
                                <SingleLinePlot
                                    key={`plot-${model.blockId}-${yval}`}
                                    plotData={plotData}
                                    yval={yval}
                                    yvalMeta={plotMeta.get(yval)}
                                    blockId={model.blockId}
                                    defaultColor={"var(--accent-color)"}
                                    title={title}
                                    targetLen={targetLen}
                                />
                            );
                        })}
                </div>
            )}
        </OverlayScrollbarsComponent>
    );
});

export { SysinfoViewModel };
