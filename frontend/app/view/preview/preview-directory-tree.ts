// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { TreeNodeData } from "@/app/treeview/treeview";

export type DirectoryViewMode = "folder" | "tree";

export const DirectoryViewModeSettingKey = "preview:directoryviewmode";

export interface DirectoryTreeNodeVisuals {
    icon?: string;
    iconColor?: string;
}

export function normalizeDirectoryViewMode(value: string | null | undefined): DirectoryViewMode {
    return value === "tree" ? "tree" : "folder";
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
        icon: visuals.icon,
        iconColor: visuals.iconColor,
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
