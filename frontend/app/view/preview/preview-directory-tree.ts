// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { TreeNodeData } from "@/app/treeview/treeview";

export type DirectoryViewMode = "tree";

export const DirectoryViewModeSettingKey = "preview:directoryviewmode";

export interface DirectoryTreeNodeVisuals {
    icon?: string;
    iconColor?: string;
}

export interface DirectoryTreeColumnResizeBounds {
    min: number;
    max: number;
}

function clampNumber(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}

export function getResizedDirectoryTreeColumnWidth(
    startWidth: number,
    deltaX: number,
    bounds: DirectoryTreeColumnResizeBounds
): number {
    return clampNumber(Math.round(startWidth + deltaX), bounds.min, bounds.max);
}

export function normalizeDirectoryViewMode(_value: string | null | undefined): DirectoryViewMode {
    return "tree";
}

export function filterDirectoryTreeEntries(
    entries: FileInfo[],
    showHiddenFiles: boolean,
    searchText: string = ""
): FileInfo[] {
    const normalizedSearch = searchText.trim().toLowerCase();
    return entries.filter((fileInfo) => {
        if (fileInfo.name == null) {
            return false;
        }
        if (fileInfo.name === "..") {
            return true;
        }
        if (!showHiddenFiles && fileInfo.name.startsWith(".")) {
            return false;
        }
        if (normalizedSearch !== "" && !fileInfo.name.toLowerCase().includes(normalizedSearch)) {
            return false;
        }
        return true;
    });
}

export function getDirectoryTreeSymlinkVisuals(fileInfo: FileInfo): DirectoryTreeNodeVisuals {
    if (!fileInfo.symlink) {
        return null;
    }
    return {
        icon: fileInfo.isdir ? "folder-tree" : "link",
        iconColor: "var(--term-bright-cyan)",
    };
}

export function fileInfoToTreeNode(
    fileInfo: FileInfo,
    parentId: string,
    visuals: DirectoryTreeNodeVisuals = {}
): TreeNodeData {
    const isParentDirectory = fileInfo.name === "..";
    return {
        id: fileInfo.path,
        parentId,
        label: fileInfo.name ?? fileInfo.path,
        path: fileInfo.path,
        isDirectory: fileInfo.isdir,
        mimeType: fileInfo.mimetype,
        size: fileInfo.size,
        modeStr: fileInfo.modestr,
        modTime: fileInfo.modtime,
        createTime: fileInfo.createtime,
        icon: visuals.icon,
        iconColor: visuals.iconColor,
        isSymlink: fileInfo.symlink,
        childrenStatus: fileInfo.isdir && !isParentDirectory ? "unloaded" : "loaded",
        clickAction: isParentDirectory ? "open" : undefined,
    };
}

export function fileInfoEntriesToTreeNodes(
    entries: FileInfo[],
    parentId: string,
    getVisuals: (fileInfo: FileInfo) => DirectoryTreeNodeVisuals = () => ({})
): { rootIds: string[]; initialNodes: Record<string, TreeNodeData> } {
    const rootIds: string[] = [];
    const initialNodes: Record<string, TreeNodeData> = {};

    entries.forEach((entry) => {
        const node = fileInfoToTreeNode(entry, parentId, getVisuals(entry));
        rootIds.push(node.id);
        initialNodes[node.id] = node;
    });

    return { rootIds, initialNodes };
}
