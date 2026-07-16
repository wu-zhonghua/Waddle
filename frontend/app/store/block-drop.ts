// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import {
    applyInheritedBlockLocation,
    CreateBlockPlacement,
    makeCreateBlockPlacementAction,
} from "@/app/store/block-placement";
import { newLayoutNode } from "@/layout/lib/layoutNode";
import { computeInsertNode } from "@/layout/lib/layoutTree";
import {
    DropDirection,
    LayoutTreeAction,
    LayoutTreeActionType,
    LayoutTreeInsertNodeAction,
    LayoutTreeState,
} from "@/layout/lib/types";

type BlockDropLayoutModel = {
    treeState: LayoutTreeState;
    focusedNodeIdHistory: readonly string[];
    treeReducer: (action: LayoutTreeAction, setState?: boolean) => void;
    focusNode: (nodeId: string) => void;
};

type CreateBlockAtLayoutPositionDeps = {
    layoutModel: BlockDropLayoutModel;
    createBlockObject: (blockDef: BlockDef, rtOpts: RuntimeOpts) => Promise<string>;
    getBlockMeta: (blockId: string) => MetaType | null;
};

async function createBlockAtLayoutPositionWithDeps(
    blockDef: BlockDef,
    targetNodeId: string,
    direction: DropDirection,
    placement: CreateBlockPlacement,
    deps: CreateBlockAtLayoutPositionDeps
): Promise<string> {
    const { layoutModel, createBlockObject, getBlockMeta } = deps;
    const resolvedBlockDef = applyInheritedBlockLocation(
        blockDef,
        layoutModel.treeState?.rootNode,
        layoutModel.treeState?.focusedNodeId,
        placement,
        getBlockMeta,
        layoutModel.focusedNodeIdHistory
    );
    const blockId = await createBlockObject(resolvedBlockDef, { termsize: { rows: 25, cols: 80 } });
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId });
    const dropAction =
        targetNodeId != null && direction != null
            ? computeInsertNode(layoutModel.treeState, targetNodeId, newNode, direction)
            : null;

    if (dropAction != null) {
        layoutModel.treeReducer(dropAction, false);
        layoutModel.focusNode(newNode.id);
        return blockId;
    }

    const placementAction =
        layoutModel.treeState?.rootNode == null || placement === "default"
            ? null
            : makeCreateBlockPlacementAction(layoutModel.treeState.rootNode, newNode, placement, getBlockMeta);
    const insertNodeAction: LayoutTreeInsertNodeAction = {
        type: LayoutTreeActionType.InsertNode,
        node: newNode,
        magnified: false,
        focused: true,
    };
    layoutModel.treeReducer(placementAction ?? insertNodeAction);
    return blockId;
}

export { createBlockAtLayoutPositionWithDeps };
export type { BlockDropLayoutModel, CreateBlockAtLayoutPositionDeps };
