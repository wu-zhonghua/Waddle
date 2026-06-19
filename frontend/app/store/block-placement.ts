// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { findNode, walkNodes } from "@/layout/lib/layoutNode";
import {
    FlexDirection,
    LayoutNode,
    LayoutTreeAction,
    LayoutTreeActionType,
    LayoutTreeInsertLeftSidebarAction,
    LayoutTreeRootRowRebalance,
    LayoutTreeSplitHorizontalAction,
    LayoutTreeSplitVerticalAction,
} from "@/layout/lib/types";

export type CreateBlockPlacement = "default" | "files" | "terminal" | "preview";

const FilesSidebarSize = 20;
const MainContentSize = 80;
const RootRowSize = 100;
const StackedBlockHeightFraction = 1 / 3;

type BlockMetaResolver = (blockId: string) => MetaType | null;
type BlockLocation = {
    connection?: string;
    path?: string;
};

function findLeafByMeta(rootNode: LayoutNode, getBlockMeta: BlockMetaResolver, predicate: (meta: MetaType) => boolean): LayoutNode {
    let match: LayoutNode = null;
    walkNodes(rootNode, (node) => {
        if (match != null || node.data?.blockId == null) {
            return;
        }
        const meta = getBlockMeta(node.data.blockId);
        if (meta != null && predicate(meta)) {
            match = node;
        }
    });
    return match;
}

function findRightmostLeaf(rootNode: LayoutNode): LayoutNode {
    let match: LayoutNode = null;
    walkNodes(rootNode, (node) => {
        if (node.data?.blockId == null) {
            return;
        }
        match = node;
    });
    return match;
}

function findRootRowChildContaining(rootNode: LayoutNode, descendant: LayoutNode): LayoutNode {
    if (rootNode == null || descendant == null) {
        return null;
    }
    if (rootNode.id === descendant.id) {
        return rootNode;
    }
    if (rootNode.flexDirection !== FlexDirection.Row || rootNode.children == null) {
        return null;
    }
    return rootNode.children.find((child) => findNode(child, descendant.id) != null) ?? null;
}

function findRootRowRightmostChild(rootNode: LayoutNode): LayoutNode {
    if (rootNode?.flexDirection === FlexDirection.Row && rootNode.children?.length) {
        return rootNode.children[rootNode.children.length - 1];
    }
    return findRightmostLeaf(rootNode);
}

function makeRootRowRebalance(sidebarNode: LayoutNode): LayoutTreeRootRowRebalance {
    const fixedSize = sidebarNode?.size ?? FilesSidebarSize;
    return {
        fixedNodeId: sidebarNode.id,
        fixedSize,
        remainingSize: Math.max(0, RootRowSize - fixedSize),
    };
}

function isFilesMeta(meta: MetaType): boolean {
    return meta.view === "preview";
}

function isTerminalMeta(meta: MetaType): boolean {
    return meta.view === "term" || meta.controller === "shell";
}

function getNodeMeta(node: LayoutNode, getBlockMeta: BlockMetaResolver): MetaType | null {
    if (node?.data?.blockId == null) {
        return null;
    }
    return getBlockMeta(node.data.blockId);
}

function isUsableLocation(location: BlockLocation): boolean {
    return location?.connection != null || location?.path != null;
}

function getBlockLocation(meta: MetaType): BlockLocation | null {
    if (meta == null) {
        return null;
    }
    if (isTerminalMeta(meta)) {
        return { connection: meta.connection, path: meta["cmd:cwd"] };
    }
    if (isFilesMeta(meta)) {
        return { connection: meta.connection, path: meta.file };
    }
    return null;
}

function getNodeLocation(node: LayoutNode, getBlockMeta: BlockMetaResolver): BlockLocation | null {
    const location = getBlockLocation(getNodeMeta(node, getBlockMeta));
    if (!isUsableLocation(location)) {
        return null;
    }
    return location;
}

function findInheritedBlockLocation(
    rootNode: LayoutNode,
    focusedNodeId: string,
    getBlockMeta: BlockMetaResolver
): BlockLocation | null {
    const focusedLocation = getNodeLocation(findNode(rootNode, focusedNodeId), getBlockMeta);
    if (focusedLocation != null) {
        return focusedLocation;
    }

    let match: BlockLocation = null;
    walkNodes(rootNode, (node) => {
        if (match != null) {
            return;
        }
        match = getNodeLocation(node, getBlockMeta);
    });
    return match;
}

function shouldInheritFilesLocation(meta: MetaType, placement: CreateBlockPlacement): boolean {
    if (!isFilesMeta(meta)) {
        return false;
    }
    return placement === "files" || meta.file == null || meta.file === "~";
}

export function applyInheritedBlockLocation(
    blockDef: BlockDef,
    rootNode: LayoutNode,
    focusedNodeId: string,
    placement: CreateBlockPlacement,
    getBlockMeta: BlockMetaResolver
): BlockDef {
    const meta = blockDef?.meta;
    if (meta == null || rootNode == null) {
        return blockDef;
    }

    const inheritsTerminalLocation = isTerminalMeta(meta);
    const inheritsFilesLocation = shouldInheritFilesLocation(meta, placement);
    if (!inheritsTerminalLocation && !inheritsFilesLocation) {
        return blockDef;
    }

    const location = findInheritedBlockLocation(rootNode, focusedNodeId, getBlockMeta);
    if (location == null) {
        return blockDef;
    }

    const inheritedMeta: MetaType = { ...meta };
    if (inheritedMeta.connection == null && location.connection != null) {
        inheritedMeta.connection = location.connection;
    }

    if (inheritsTerminalLocation && inheritedMeta["cmd:cwd"] == null && location.path != null) {
        inheritedMeta["cmd:cwd"] = location.path;
    }
    if (inheritsFilesLocation && (inheritedMeta.file == null || inheritedMeta.file === "~") && location.path != null) {
        inheritedMeta.file = location.path;
    }

    return { ...blockDef, meta: inheritedMeta };
}

function makeSplitVerticalAction(targetNode: LayoutNode, newNode: LayoutNode): LayoutTreeSplitVerticalAction {
    newNode.size = targetNode.size * (StackedBlockHeightFraction / (1 - StackedBlockHeightFraction));
    return {
        type: LayoutTreeActionType.SplitVertical,
        targetNodeId: targetNode.id,
        newNode,
        position: "after",
        focused: true,
    };
}

function makeSplitHorizontalAction(
    targetNode: LayoutNode,
    newNode: LayoutNode,
    targetNodeSize?: number,
    rebalanceRootRow?: LayoutTreeRootRowRebalance
): LayoutTreeSplitHorizontalAction {
    return {
        type: LayoutTreeActionType.SplitHorizontal,
        targetNodeId: targetNode.id,
        newNode,
        position: "after",
        focused: true,
        targetNodeSize,
        rebalanceRootRow,
    };
}

export function makeCreateBlockPlacementAction(
    rootNode: LayoutNode,
    newNode: LayoutNode,
    placement: CreateBlockPlacement,
    getBlockMeta: BlockMetaResolver
): LayoutTreeAction {
    if (placement === "files") {
        const existingFiles = findLeafByMeta(rootNode, getBlockMeta, isFilesMeta);
        if (existingFiles != null) {
            return makeSplitVerticalAction(existingFiles, newNode);
        }
        return {
            type: LayoutTreeActionType.InsertLeftSidebar,
            node: newNode,
            magnified: false,
            focused: true,
            sidebarSize: FilesSidebarSize,
            mainSize: MainContentSize,
        } as LayoutTreeInsertLeftSidebarAction;
    }
    if (placement === "terminal") {
        const existingTerminal = findLeafByMeta(rootNode, getBlockMeta, isTerminalMeta);
        if (existingTerminal != null) {
            return makeSplitVerticalAction(existingTerminal, newNode);
        }
        const existingFiles = findLeafByMeta(rootNode, getBlockMeta, isFilesMeta);
        if (existingFiles != null) {
            newNode.size = MainContentSize;
            return makeSplitHorizontalAction(existingFiles, newNode);
        }
    }
    if (placement === "preview") {
        const existingFiles = findLeafByMeta(rootNode, getBlockMeta, isFilesMeta);
        if (existingFiles != null) {
            const sidebarNode = findRootRowChildContaining(rootNode, existingFiles);
            const targetNode = findRootRowRightmostChild(rootNode);
            const sidebarIsRootLeftChild =
                rootNode?.children == null || rootNode.children.length === 0 || rootNode.children[0].id === sidebarNode?.id;
            if (sidebarNode != null && targetNode != null && sidebarIsRootLeftChild) {
                if (sidebarNode.id === targetNode.id) {
                    newNode.size = MainContentSize;
                }
                return makeSplitHorizontalAction(targetNode, newNode, undefined, makeRootRowRebalance(sidebarNode));
            }
        }
        const rightmostLeaf = findRightmostLeaf(rootNode);
        if (rightmostLeaf == null) {
            return null;
        }
        const splitSize = rightmostLeaf.size / 2;
        newNode.size = splitSize;
        return makeSplitHorizontalAction(rightmostLeaf, newNode, splitSize);
    }
    return null;
}
