// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { makeIconClass } from "@/util/util";
import { useVirtualizer } from "@tanstack/react-virtual";
import clsx from "clsx";
import React, {
    CSSProperties,
    KeyboardEvent,
    MouseEvent,
    forwardRef,
    useCallback,
    useEffect,
    useImperativeHandle,
    useMemo,
    useRef,
    useState,
} from "react";

type TreeNodeChildrenStatus = "unloaded" | "loading" | "loaded" | "error" | "capped";
export type TreeNodeClickAction = "select" | "toggle" | "open";
export type TreeSortField = "name" | "type" | "modified" | "created" | "size";
export type TreeSortDirection = "asc" | "desc";

export interface TreeSortSpec {
    field: TreeSortField;
    direction: TreeSortDirection;
}

export interface TreeNodeData {
    id: string;
    parentId?: string;
    label?: string;
    path?: string;
    isDirectory: boolean;
    mimeType?: string;
    size?: number;
    modeStr?: string;
    modTime?: number;
    createTime?: number;
    icon?: string;
    iconColor?: string;
    isReadonly?: boolean;
    notfound?: boolean;
    staterror?: string;
    childrenStatus?: TreeNodeChildrenStatus;
    childrenIds?: string[];
    capInfo?: { max: number; totalKnown?: number };
    clickAction?: TreeNodeClickAction;
}

interface FetchDirResult {
    nodes: TreeNodeData[];
    capped?: boolean;
    totalKnown?: number;
}

export interface TreeViewVisibleRow {
    id: string;
    parentId?: string;
    depth: number;
    kind: "node" | "loading" | "error" | "capped";
    label: string;
    isDirectory?: boolean;
    isExpanded?: boolean;
    hasChildren?: boolean;
    icon?: string;
    node?: TreeNodeData;
}

export interface TreeViewProps {
    rootIds: string[];
    initialNodes: Record<string, TreeNodeData>;
    fetchDir?: (id: string, limit: number) => Promise<FetchDirResult>;
    maxDirEntries?: number;
    rowHeight?: number;
    indentWidth?: number;
    overscan?: number;
    minWidth?: number;
    maxWidth?: number;
    width?: number | string;
    height?: number | string;
    className?: string;
    sortSpec?: TreeSortSpec;
    renderNodeDetails?: (node: TreeNodeData) => React.ReactNode;
    onOpenFile?: (id: string, node: TreeNodeData) => void;
    onSelectionChange?: (id: string, node: TreeNodeData) => void;
    onNodeContextMenu?: (event: MouseEvent<HTMLDivElement>, id: string, node: TreeNodeData) => void;
    reloadSignal?: unknown;
}

export interface TreeViewRef {
    scrollToId: (id: string) => void;
}

const DefaultRowHeight = 24;
const DefaultIndentWidth = 16;
const DefaultOverscan = 10;
const ChevronWidth = 16;
const DefaultSortSpec: TreeSortSpec = { field: "name", direction: "asc" };

function normalizeLabel(node: TreeNodeData): string {
    if (node.label?.trim()) {
        return node.label;
    }
    const path = node.path ?? node.id;
    const chunks = path.split("/").filter(Boolean);
    return chunks[chunks.length - 1] ?? path;
}

function getSortValue(node: TreeNodeData, field: TreeSortField): string | number {
    switch (field) {
        case "type":
            return node.mimeType ?? (node.isDirectory ? "directory" : "");
        case "modified":
            return node.modTime;
        case "created":
            return node.createTime;
        case "size":
            return node.size;
        case "name":
        default:
            return normalizeLabel(node).toLocaleLowerCase();
    }
}

function compareMaybeNumber(leftValue: string | number, rightValue: string | number): number {
    if (typeof leftValue === "number" || typeof rightValue === "number") {
        const leftFinite = Number.isFinite(leftValue);
        const rightFinite = Number.isFinite(rightValue);
        if (leftFinite && rightFinite) {
            return (leftValue as number) - (rightValue as number);
        }
        if (leftFinite !== rightFinite) {
            return leftFinite ? -1 : 1;
        }
        return 0;
    }
    return leftValue.localeCompare(rightValue);
}

function compareNodes(left: TreeNodeData, right: TreeNodeData, sortSpec: TreeSortSpec): number {
    const leftParent = normalizeLabel(left) === ".." ? 0 : 1;
    const rightParent = normalizeLabel(right) === ".." ? 0 : 1;
    if (leftParent !== rightParent) {
        return leftParent - rightParent;
    }
    const leftDir = left.isDirectory ? 0 : 1;
    const rightDir = right.isDirectory ? 0 : 1;
    if (leftDir !== rightDir) {
        return leftDir - rightDir;
    }
    const directionMultiplier = sortSpec.direction === "desc" ? -1 : 1;
    const sortCompare =
        compareMaybeNumber(getSortValue(left, sortSpec.field), getSortValue(right, sortSpec.field)) * directionMultiplier;
    if (sortCompare !== 0) {
        return sortCompare;
    }
    const leftLabel = normalizeLabel(left).toLocaleLowerCase();
    const rightLabel = normalizeLabel(right).toLocaleLowerCase();
    if (leftLabel !== rightLabel) {
        return leftLabel.localeCompare(rightLabel);
    }
    return left.id.localeCompare(right.id);
}

function sortIdsByNode(nodesById: Map<string, TreeNodeData>, ids: string[], sortSpec: TreeSortSpec = DefaultSortSpec): string[] {
    return [...ids].sort((leftId, rightId) => {
        const left = nodesById.get(leftId) ?? { id: leftId, isDirectory: false };
        const right = nodesById.get(rightId) ?? { id: rightId, isDirectory: false };
        return compareNodes(left, right, sortSpec);
    });
}

function normalizeInitialTreeNode(node: TreeNodeData): TreeNodeData {
    return {
        ...node,
        childrenStatus: node.childrenStatus ?? "unloaded",
    };
}

function makeInitialTreeNodeMap(initialNodes: Record<string, TreeNodeData>): Map<string, TreeNodeData> {
    return new Map(Object.entries(initialNodes).map(([id, node]) => [id, normalizeInitialTreeNode(node)]));
}

export function mergeInitialTreeNodes(
    currentNodes: Map<string, TreeNodeData>,
    initialNodes: Record<string, TreeNodeData>
): Map<string, TreeNodeData> {
    const next = new Map(currentNodes);

    Object.entries(initialNodes).forEach(([id, node]) => {
        const currentNode = currentNodes.get(id);
        const refreshedNode = normalizeInitialTreeNode(node);
        if (!currentNode?.isDirectory || !refreshedNode.isDirectory || currentNode.childrenStatus === "unloaded") {
            next.set(id, refreshedNode);
            return;
        }
        next.set(id, {
            ...refreshedNode,
            childrenIds: currentNode.childrenIds,
            childrenStatus: currentNode.childrenStatus,
            capInfo: currentNode.capInfo,
            staterror: currentNode.staterror,
        });
    });

    return next;
}

export function buildVisibleRows(
    nodesById: Map<string, TreeNodeData>,
    rootIds: string[],
    expandedIds: Set<string>,
    sortSpec: TreeSortSpec = DefaultSortSpec
): TreeViewVisibleRow[] {
    const rows: TreeViewVisibleRow[] = [];

    const appendNode = (id: string, depth: number) => {
        const node = nodesById.get(id);
        if (node == null) {
            return;
        }
        const childIds = node.childrenIds ?? [];
        const hasChildren = node.isDirectory && (childIds.length > 0 || node.childrenStatus !== "loaded");
        const isExpanded = expandedIds.has(id);
        rows.push({
            id,
            parentId: node.parentId,
            depth,
            kind: "node",
            label: normalizeLabel(node),
            isDirectory: node.isDirectory,
            isExpanded,
            hasChildren,
            icon: node.icon,
            node,
        });
        if (!isExpanded || !node.isDirectory) {
            return;
        }
        const status = node.childrenStatus ?? "unloaded";
        if (status === "loading") {
            rows.push({
                id: `${id}::__loading`,
                parentId: id,
                depth: depth + 1,
                kind: "loading",
                label: "Loading…",
            });
            return;
        }
        if (status === "error") {
            rows.push({
                id: `${id}::__error`,
                parentId: id,
                depth: depth + 1,
                kind: "error",
                label: node.staterror ? `Error: ${node.staterror}` : "Unable to load directory",
            });
            return;
        }

        const sortedChildren = sortIdsByNode(nodesById, childIds, sortSpec);
        sortedChildren.forEach((childId) => appendNode(childId, depth + 1));
        if (status === "capped") {
            const capMax = node.capInfo?.max ?? childIds.length;
            rows.push({
                id: `${id}::__capped`,
                parentId: id,
                depth: depth + 1,
                kind: "capped",
                label: `Showing first ${capMax} entries`,
            });
        }
    };

    sortIdsByNode(nodesById, rootIds, sortSpec).forEach((id) => appendNode(id, 0));
    return rows;
}

export function getTreeNodeClickAction(row: TreeViewVisibleRow): TreeNodeClickAction {
    if (row.kind !== "node") {
        return "select";
    }
    if (row.node?.clickAction != null) {
        return row.node.clickAction;
    }
    if (row.isDirectory) {
        return "toggle";
    }
    return "select";
}

function getNodeIcon(node: TreeNodeData, isExpanded: boolean): string {
    if (node.notfound || node.staterror) {
        return "triangle-exclamation";
    }
    if (node.icon) {
        return node.icon;
    }
    if (node.isDirectory) {
        return isExpanded ? "folder-open" : "folder";
    }
    const mime = node.mimeType ?? "";
    if (mime.startsWith("image/")) {
        return "image";
    }
    if (mime === "application/pdf") {
        return "file-pdf";
    }
    const extension = normalizeLabel(node).split(".").pop()?.toLocaleLowerCase();
    if (["js", "jsx", "ts", "tsx", "go", "py", "java", "c", "cpp", "h", "hpp", "json", "yaml", "yml"].includes(extension)) {
        return "file-code";
    }
    if (["md", "txt", "log"].includes(extension)) {
        return "file-lines";
    }
    return "file";
}

export const TreeView = forwardRef<TreeViewRef, TreeViewProps>((props, ref) => {
    const {
        rootIds,
        initialNodes,
        fetchDir,
        maxDirEntries = 500,
        rowHeight = DefaultRowHeight,
        indentWidth = DefaultIndentWidth,
        overscan = DefaultOverscan,
        minWidth = 100,
        maxWidth = 400,
        width = "100%",
        height = 360,
        className,
        sortSpec = DefaultSortSpec,
        renderNodeDetails,
        onOpenFile,
        onSelectionChange,
        onNodeContextMenu,
        reloadSignal,
    } = props;
    const [nodesById, setNodesById] = useState<Map<string, TreeNodeData>>(() => makeInitialTreeNodeMap(initialNodes));
    const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
    const [selectedId, setSelectedId] = useState<string>(rootIds[0]);
    const scrollRef = useRef<HTMLDivElement>(null);
    const lastReloadSignalRef = useRef<unknown>(undefined);

    useEffect(() => {
        setNodesById((currentNodes) => mergeInitialTreeNodes(currentNodes, initialNodes));
    }, [initialNodes]);

    const visibleRows = useMemo(
        () => buildVisibleRows(nodesById, rootIds, expandedIds, sortSpec),
        [nodesById, rootIds, expandedIds, sortSpec]
    );
    const idToIndex = useMemo(
        () => new Map(visibleRows.map((row, index) => [row.id, index])),
        [visibleRows]
    );
    const virtualizer = useVirtualizer({
        count: visibleRows.length,
        getScrollElement: () => scrollRef.current,
        estimateSize: () => rowHeight,
        overscan,
    });

    const commitSelection = (id: string) => {
        const node = nodesById.get(id);
        if (node == null) {
            return;
        }
        setSelectedId(id);
        onSelectionChange?.(id, node);
    };

    const scrollToId = (id: string) => {
        const index = idToIndex.get(id);
        if (index == null) {
            return;
        }
        virtualizer.scrollToIndex(index, { align: "auto" });
    };

    useImperativeHandle(
        ref,
        () => ({
            scrollToId,
        }),
        [idToIndex, virtualizer]
    );

    const loadChildren = useCallback(async (id: string, force = false) => {
        const currentNode = nodesById.get(id);
        if (
            currentNode == null ||
            !currentNode.isDirectory ||
            currentNode.notfound ||
            (!force && currentNode.staterror) ||
            fetchDir == null
        ) {
            return;
        }
        const status = currentNode.childrenStatus ?? "unloaded";
        if (!force && status !== "unloaded") {
            return;
        }
        setNodesById((prev) => {
            const next = new Map(prev);
            const source = next.get(id) ?? currentNode;
            next.set(id, { ...source, childrenStatus: "loading", staterror: undefined });
            return next;
        });
        try {
            const result = await fetchDir(id, maxDirEntries);
            setNodesById((prev) => {
                const next = new Map(prev);
                const source = next.get(id) ?? currentNode;
                result.nodes.forEach((node) => {
                    const merged: TreeNodeData = {
                        ...node,
                        parentId: node.parentId ?? id,
                        childrenStatus: node.childrenStatus ?? (node.isDirectory ? "unloaded" : "loaded"),
                    };
                    next.set(merged.id, merged);
                });
                const childrenIds = sortIdsByNode(
                    next,
                    result.nodes.map((entry) => entry.id),
                    sortSpec
                );
                next.set(id, {
                    ...source,
                    childrenIds,
                    childrenStatus: result.capped ? "capped" : "loaded",
                    capInfo: result.capped ? { max: maxDirEntries, totalKnown: result.totalKnown } : undefined,
                });
                return next;
            });
        } catch (error) {
            setNodesById((prev) => {
                const next = new Map(prev);
                const source = next.get(id) ?? currentNode;
                next.set(id, {
                    ...source,
                    childrenStatus: "error",
                    staterror: error instanceof Error ? error.message : "Unknown error",
                });
                return next;
            });
        }
    }, [fetchDir, maxDirEntries, nodesById, sortSpec]);

    useEffect(() => {
        if (reloadSignal == null || Object.is(lastReloadSignalRef.current, reloadSignal)) {
            return;
        }
        lastReloadSignalRef.current = reloadSignal;
        expandedIds.forEach((id) => {
            const node = nodesById.get(id);
            if (node?.isDirectory) {
                loadChildren(id, true);
            }
        });
    }, [expandedIds, loadChildren, nodesById, reloadSignal]);

    const toggleExpand = (id: string) => {
        const node = nodesById.get(id);
        if (node == null || !node.isDirectory || node.notfound || node.staterror) {
            return;
        }
        const expanded = expandedIds.has(id);
        if (!expanded) {
            loadChildren(id);
        }
        setExpandedIds((prev) => {
            const next = new Set(prev);
            if (expanded) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
        scrollToId(id);
    };

    const selectVisibleNodeAt = (index: number) => {
        if (index < 0 || index >= visibleRows.length) {
            return;
        }
        const row = visibleRows[index];
        if (row.kind !== "node") {
            return;
        }
        commitSelection(row.id);
        scrollToId(row.id);
    };

    const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        const selectedIndex = selectedId != null ? idToIndex.get(selectedId) : undefined;
        if (event.key === "ArrowDown") {
            event.preventDefault();
            const nextIndex = (selectedIndex ?? -1) + 1;
            for (let idx = nextIndex; idx < visibleRows.length; idx++) {
                if (visibleRows[idx].kind === "node") {
                    selectVisibleNodeAt(idx);
                    break;
                }
            }
            return;
        }
        if (event.key === "ArrowUp") {
            event.preventDefault();
            const previousIndex = (selectedIndex ?? visibleRows.length) - 1;
            for (let idx = previousIndex; idx >= 0; idx--) {
                if (visibleRows[idx].kind === "node") {
                    selectVisibleNodeAt(idx);
                    break;
                }
            }
            return;
        }
        const node = selectedId ? nodesById.get(selectedId) : null;
        if (node == null) {
            return;
        }
        if (event.key === "ArrowLeft") {
            event.preventDefault();
            if (node.isDirectory && expandedIds.has(node.id)) {
                toggleExpand(node.id);
                return;
            }
            if (node.parentId != null) {
                commitSelection(node.parentId);
                scrollToId(node.parentId);
            }
            return;
        }
        if (event.key === "ArrowRight") {
            event.preventDefault();
            if (node.isDirectory && !expandedIds.has(node.id)) {
                toggleExpand(node.id);
                return;
            }
            if (node.isDirectory && expandedIds.has(node.id) && node.childrenIds?.[0]) {
                commitSelection(node.childrenIds[0]);
                scrollToId(node.childrenIds[0]);
            }
        }
    };

    const containerStyle: CSSProperties = {
        width,
        minWidth,
        maxWidth,
        height,
    };

    return (
        <div
            className={clsx("rounded-md border border-border bg-panel", className)}
            style={containerStyle}
            tabIndex={0}
            onKeyDown={onKeyDown}
        >
            <div ref={scrollRef} className="h-full overflow-auto">
                <div className="relative w-max min-w-full" style={{ height: virtualizer.getTotalSize() }}>
                    {virtualizer.getVirtualItems().map((virtualRow) => {
                        const row = visibleRows[virtualRow.index];
                        if (row.kind === "node" && row.node == null) {
                            return null;
                        }
                        const selected = row.id === selectedId;
                        const nodeDetails =
                            row.kind === "node" && row.node != null ? renderNodeDetails?.(row.node) : null;
                        return (
                            <div
                                key={row.id}
                                className={clsx(
                                    "tree-node-row absolute left-0 right-0 flex items-center whitespace-nowrap text-sm",
                                    row.kind === "node" ? "cursor-pointer" : "text-muted",
                                    row.kind !== "node" && "tree-node-synthetic",
                                    selected ? "bg-accent/25 text-foreground" : "text-foreground hover:bg-muted/50"
                                )}
                                style={{
                                    top: 0,
                                    height: rowHeight,
                                    transform: `translateY(${virtualRow.start}px)`,
                                }}
                                onClick={() => {
                                    if (row.kind !== "node") {
                                        return;
                                    }
                                    commitSelection(row.id);
                                    const clickAction = getTreeNodeClickAction(row);
                                    if (clickAction === "toggle") {
                                        toggleExpand(row.id);
                                    }
                                    if (clickAction === "open" && row.node != null) {
                                        onOpenFile?.(row.id, row.node);
                                    }
                                }}
                                onContextMenu={(event) => {
                                    if (row.kind === "node" && row.node != null) {
                                        onNodeContextMenu?.(event, row.id, row.node);
                                    }
                                }}
                                onDoubleClick={() => {
                                    if (row.kind !== "node") {
                                        return;
                                    }
                                    if (row.node != null) {
                                        onOpenFile?.(row.id, row.node);
                                    }
                                }}
                            >
                                {row.kind === "node" ? (
                                    <>
                                        <div className="tree-node-main flex min-w-0 items-center">
                                            <div
                                                className="flex items-center"
                                                style={{
                                                    paddingLeft: row.depth * indentWidth,
                                                    width: ChevronWidth + row.depth * indentWidth,
                                                }}
                                            >
                                                {row.isDirectory && row.hasChildren ? (
                                                    <button
                                                        className="h-4 w-4 rounded text-muted hover:text-foreground cursor-pointer"
                                                        onClick={(event: MouseEvent<HTMLButtonElement>) => {
                                                            event.stopPropagation();
                                                            toggleExpand(row.id);
                                                        }}
                                                    >
                                                        <i
                                                            className={clsx(
                                                                "fa-sharp fa-solid text-[11px]",
                                                                row.isExpanded ? "fa-chevron-down" : "fa-chevron-right"
                                                            )}
                                                        />
                                                    </button>
                                                ) : (
                                                    <span className="inline-block h-4 w-4" />
                                                )}
                                            </div>
                                            <i
                                                className={makeIconClass(getNodeIcon(row.node, row.isExpanded), true)}
                                                style={{
                                                    color:
                                                        row.node.notfound || row.node.staterror
                                                            ? "var(--color-error)"
                                                            : (row.node.iconColor ?? "inherit"),
                                                }}
                                            />
                                            <span
                                                className={clsx(
                                                    "ml-2 min-w-0 flex-1 truncate pr-3",
                                                    row.node.isReadonly && "text-muted"
                                                )}
                                                title={row.label}
                                            >
                                                {row.label}
                                            </span>
                                        </div>
                                        {nodeDetails != null && (
                                            <div className="tree-node-details ml-auto flex shrink-0 items-center gap-3 pr-2 text-xs text-muted">
                                                {nodeDetails}
                                            </div>
                                        )}
                                    </>
                                ) : (
                                    <span className="tree-node-main ml-6 pr-3 text-xs">{row.label}</span>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
});

TreeView.displayName = "TreeView";
