// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { DropDirection, LayoutNode } from "./types";

const TileDragItemType = "TILE_ITEM";
const ExternalTileDragItemType = "EXTERNAL_TILE_ITEM";

type ExternalTileDragItem = {
    node: LayoutNode;
    onDrop: (targetNodeId?: string, direction?: DropDirection) => Promise<void>;
};

function isExternalTileDropDirection(direction?: DropDirection): boolean {
    return direction != null && direction !== DropDirection.Center;
}

async function dispatchExternalTileDrop(
    item: ExternalTileDragItem,
    targetNodeId?: string,
    direction?: DropDirection,
    emptyLayout = false
): Promise<boolean> {
    if (!emptyLayout && !isExternalTileDropDirection(direction)) {
        return false;
    }
    await item.onDrop(targetNodeId, direction);
    return true;
}

export {
    dispatchExternalTileDrop,
    ExternalTileDragItemType,
    isExternalTileDropDirection,
    TileDragItemType,
};
export type { ExternalTileDragItem };
