// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import {
    dispatchExternalTileDrop,
    ExternalTileDragItemType,
    isExternalTileDropDirection,
    TileDragItemType,
} from "./lib/drag";
import type { ExternalTileDragItem } from "./lib/drag";
import { TileLayout } from "./lib/TileLayout";
import { LayoutModel } from "./lib/layoutModel";
import { deleteLayoutModelForTab, getLayoutModelForStaticTab, useDebouncedNodeInnerRect } from "./lib/layoutModelHooks";
import { newLayoutNode } from "./lib/layoutNode";
import { computeInsertNode } from "./lib/layoutTree";
import type {
    ContentRenderer,
    LayoutNode,
    LayoutTreeAction,
    LayoutTreeClearPendingAction,
    LayoutTreeCommitPendingAction,
    LayoutTreeComputeMoveNodeAction,
    LayoutTreeDeleteNodeAction,
    LayoutTreeFocusNodeAction,
    LayoutTreeInsertNodeAction,
    LayoutTreeInsertNodeAtIndexAction,
    LayoutTreeInsertLeftSidebarAction,
    LayoutTreeMagnifyNodeToggleAction,
    LayoutTreeMoveNodeAction,
    LayoutTreeResizeNodeAction,
    LayoutTreeSetPendingAction,
    LayoutTreeStateSetter,
    LayoutTreeSwapNodeAction,
    NodeModel,
    PreviewRenderer,
} from "./lib/types";
import { DropDirection, LayoutTreeActionType, NavigateDirection } from "./lib/types";

export {
    computeInsertNode,
    deleteLayoutModelForTab,
    dispatchExternalTileDrop,
    DropDirection,
    ExternalTileDragItemType,
    getLayoutModelForStaticTab,
    isExternalTileDropDirection,
    LayoutModel,
    LayoutTreeActionType,
    NavigateDirection,
    newLayoutNode,
    TileDragItemType,
    TileLayout,
    useDebouncedNodeInnerRect,
};
export type {
    ContentRenderer,
    ExternalTileDragItem,
    LayoutNode,
    LayoutTreeAction,
    LayoutTreeClearPendingAction,
    LayoutTreeCommitPendingAction,
    LayoutTreeComputeMoveNodeAction,
    LayoutTreeDeleteNodeAction,
    LayoutTreeFocusNodeAction,
    LayoutTreeInsertNodeAction,
    LayoutTreeInsertNodeAtIndexAction,
    LayoutTreeInsertLeftSidebarAction,
    LayoutTreeMagnifyNodeToggleAction,
    LayoutTreeMoveNodeAction,
    LayoutTreeResizeNodeAction,
    LayoutTreeSetPendingAction,
    LayoutTreeStateSetter,
    LayoutTreeSwapNodeAction,
    NodeModel,
    PreviewRenderer,
};
