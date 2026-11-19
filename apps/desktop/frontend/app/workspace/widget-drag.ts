// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { CreateBlockPlacement, getPlacementForBlockDef } from "@/app/store/block-placement";
import { ExternalTileDragItem } from "@/layout/lib/drag";
import { newLayoutNode } from "@/layout/lib/layoutNode";
import { DropDirection } from "@/layout/lib/types";

const WidgetDragPreviewBlockId = "__widget_drag_preview__";

type CreateBlockAtLayoutPosition = (
    blockDef: BlockDef,
    targetNodeId?: string,
    direction?: DropDirection,
    placement?: CreateBlockPlacement
) => Promise<string>;

function makeWidgetDragItem(
    widget: WidgetConfigType,
    createBlockAtLayoutPosition: CreateBlockAtLayoutPosition
): ExternalTileDragItem {
    return {
        node: newLayoutNode(undefined, undefined, undefined, { blockId: WidgetDragPreviewBlockId }),
        onDrop: (targetNodeId, direction) =>
            createBlockAtLayoutPosition(
                widget.blockdef,
                targetNodeId,
                direction,
                getPlacementForBlockDef(widget.blockdef)
            ).then(() => undefined),
    };
}

export { makeWidgetDragItem };
export type { CreateBlockAtLayoutPosition };
