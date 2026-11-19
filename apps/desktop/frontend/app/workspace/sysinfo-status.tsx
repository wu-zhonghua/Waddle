// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { Tooltip } from "@/app/element/tooltip";
import { WOS } from "@/app/store/global";
import { waveEventSubscribeSingle } from "@/app/store/wps";
import { TabRpcClient } from "@/app/store/wshrpcutil";
import {
    convertWaddleEventToDataItem,
    getDefaultPlotMeta,
    makeMetricReading,
    type DataItem,
    type MetricReading,
} from "@/app/view/sysinfo/sysinfo";
import { useWaddleEnv, WaddleEnv, WaddleEnvSubset } from "@/app/waveenv/waveenv";
import { getLayoutModelForStaticTab, type LayoutNode } from "@/layout/index";
import { isBlank } from "@/util/util";
import clsx from "clsx";
import * as jotai from "jotai";
import * as React from "react";

export type WorkspaceSysinfoStatusEnv = WaddleEnvSubset<{
    rpc: {
        EventReadHistoryCommand: WaddleEnv["rpc"]["EventReadHistoryCommand"];
    };
    getBlockMetaKeyAtom: WaddleEnv["getBlockMetaKeyAtom"];
    getConnStatusAtom: WaddleEnv["getConnStatusAtom"];
}>;

const LocalConnectionAtom = jotai.atom("local");
const NullFocusedNodeAtom = jotai.atom<LayoutNode>(null);
const EmptyDataItem: DataItem = { ts: 0 };
const CoreStatusMetrics = ["cpu", "mem:used", "gpu:util", "gpu:memused"];
const OptionalStatusMetrics = ["net:download", "net:upload"];
const StatusMetricNames: Record<string, string> = {
    cpu: "CPU",
    "mem:used": "RAM",
    "gpu:util": "GPU",
    "gpu:memused": "VRAM",
    "net:download": "DN",
    "net:upload": "UP",
};

export function getSysinfoStatusMetricIds(dataItem: DataItem): string[] {
    const metrics = [...CoreStatusMetrics];
    for (const metric of OptionalStatusMetrics) {
        if (dataItem?.[metric] != null) {
            metrics.push(metric);
        }
    }
    return metrics;
}

export function makeSysinfoStatusReadings(
    dataItem: DataItem,
    previousDataItem: DataItem,
    plotMeta: Map<string, TimeSeriesMeta>
): MetricReading[] {
    const current = dataItem ?? EmptyDataItem;
    return getSysinfoStatusMetricIds(current).map((metric) =>
        makeMetricReading(metric, current, previousDataItem, plotMeta.get(metric))
    );
}

function getShortConnName(connName: string): string {
    if (isBlank(connName) || connName == "local") {
        return "local";
    }
    return connName;
}

function useFocusedConnection(tabId: string, env: WorkspaceSysinfoStatusEnv): string {
    const tabAtom = React.useMemo(() => WOS.getWaddleObjectAtom<Tab>(WOS.makeORef("tab", tabId)), [tabId]);
    jotai.useAtomValue(tabAtom);
    const layoutModel = getLayoutModelForStaticTab();
    const focusedNodeAtom = layoutModel?.focusedNode ?? NullFocusedNodeAtom;
    const focusedNode = jotai.useAtomValue(focusedNodeAtom);
    const focusedBlockId = focusedNode?.data?.blockId;
    const connectionAtom = React.useMemo(() => {
        if (focusedBlockId == null) {
            return LocalConnectionAtom;
        }
        return env.getBlockMetaKeyAtom(focusedBlockId, "connection");
    }, [env, focusedBlockId]);
    const connName = jotai.useAtomValue(connectionAtom);
    if (isBlank(connName)) {
        return "local";
    }
    return connName;
}

function SysinfoStatusMetric({ reading }: { reading: MetricReading }) {
    const metricName = StatusMetricNames[reading.metric] ?? reading.name;
    const isMissing = reading.value == "--";
    const content = `${reading.name}: ${reading.value}${reading.unit ? ` ${reading.unit}` : ""}`;

    return (
        <Tooltip content={content} placement="top" openDelay={250}>
            <div className="flex h-full min-w-0 items-center gap-1.5 rounded-[6px] px-1.5 text-[11px] text-secondary">
                <span
                    className="h-1.5 w-1.5 shrink-0 rounded-full"
                    style={{ backgroundColor: isMissing ? "var(--grey-text-color)" : reading.color }}
                />
                <span className="shrink-0 text-[10px] font-medium uppercase tracking-normal text-muted">
                    {metricName}
                </span>
                <span
                    className={clsx(
                        "min-w-[30px] shrink-0 text-right font-mono tabular-nums text-primary",
                        isMissing && "text-muted"
                    )}
                >
                    {reading.value}
                </span>
                {reading.unit && <span className="shrink-0 text-[10px] text-muted">{reading.unit}</span>}
            </div>
        </Tooltip>
    );
}

function WorkspaceSysinfoStatus({ tabId }: { tabId: string }) {
    const env = useWaddleEnv<WorkspaceSysinfoStatusEnv>();
    const connName = useFocusedConnection(tabId, env);
    const connStatusAtom = React.useMemo(() => env.getConnStatusAtom(connName), [env, connName]);
    const connStatus = jotai.useAtomValue(connStatusAtom);
    const [latestDataItem, setLatestDataItem] = React.useState<DataItem>(null);
    const [previousDataItem, setPreviousDataItem] = React.useState<DataItem>(null);
    const latestDataItemRef = React.useRef<DataItem>(null);
    const plotMeta = React.useMemo(() => getDefaultPlotMeta(), []);

    const applyDataItem = React.useCallback((dataItem: DataItem) => {
        if (dataItem == null) {
            return;
        }
        setPreviousDataItem(latestDataItemRef.current);
        latestDataItemRef.current = dataItem;
        setLatestDataItem(dataItem);
    }, []);

    React.useEffect(() => {
        let cancelled = false;
        latestDataItemRef.current = null;
        setLatestDataItem(null);
        setPreviousDataItem(null);
        env.rpc
            .EventReadHistoryCommand(TabRpcClient, {
                event: "sysinfo",
                scope: connName,
                maxitems: 2,
            })
            .then((events) => {
                if (cancelled) {
                    return;
                }
                const dataItems = (events ?? []).map(convertWaddleEventToDataItem).filter((item) => item != null);
                const previous = dataItems.length > 1 ? dataItems[dataItems.length - 2] : null;
                const latest = dataItems[dataItems.length - 1] ?? null;
                latestDataItemRef.current = latest;
                setPreviousDataItem(previous);
                setLatestDataItem(latest);
            })
            .catch((e) => {
                console.log("Error loading sysinfo status history", e);
            });
        return () => {
            cancelled = true;
        };
    }, [connName, env.rpc]);

    React.useEffect(() => {
        return waveEventSubscribeSingle({
            eventType: "sysinfo",
            scope: connName,
            handler: (event) => {
                applyDataItem(convertWaddleEventToDataItem(event));
            },
        });
    }, [connName, applyDataItem]);

    const readings = React.useMemo(
        () => makeSysinfoStatusReadings(latestDataItem, previousDataItem, plotMeta),
        [latestDataItem, previousDataItem, plotMeta]
    );
    const isConnected = connStatus?.status == null || connStatus.status == "connected";
    const shortConnName = getShortConnName(connName);

    return (
        <div
            className={clsx(
                "flex h-[30px] shrink-0 items-center gap-2 overflow-hidden border-t border-border bg-black/25 px-2 text-secondary",
                !isConnected && "opacity-70"
            )}
        >
            <div className="flex min-w-0 shrink-0 items-center gap-1.5 text-[11px] text-muted">
                <i className="fa-solid fa-chart-line text-[10px] text-accent" />
                <span className="max-w-[180px] truncate">{shortConnName}</span>
            </div>
            <div className="flex min-w-0 flex-1 items-center gap-1 overflow-hidden">
                {readings.map((reading) => (
                    <SysinfoStatusMetric key={reading.metric} reading={reading} />
                ))}
            </div>
        </div>
    );
}

export { WorkspaceSysinfoStatus };
