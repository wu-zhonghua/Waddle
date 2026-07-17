# Widget Drag Equal-Split Design

## Goal

Make a widget dropped on any existing tile divide the affected layout space evenly, so the previewed half-tile insertion matches the committed layout.

## Behavior

- Dropping on the top, right, bottom, or left edge of a tile gives the existing tile and the new tile equal shares of that tile's previous space.
- Dropping in an outer-edge region keeps the new tile at the surrounding layout level and gives it half of the adjacent group's previous share.
- Other siblings retain their sizes, so an existing Files sidebar or unrelated pane does not shrink unexpectedly.
- Center, outside, and canceled drops remain invalid and create nothing.
- Widget click placement and existing tile-to-tile movement keep their current sizing behavior.
- Empty-layout drops remain a single full-size tile.

## Root Cause

The placeholder already renders as half of the insertion anchor, but the external node is committed with `DefaultNodeSize` (`10`). When a main pane has size `80`, a right-edge drop therefore produces an `80:10` ratio instead of the previewed `40:40` split.

## Architecture

Extend the computed external `Move` action with optional resize operations. `computeInsertNode` will resolve the actual insertion anchor at the action's layout level:

- For an inner edge, the anchor is the target tile.
- For an outer edge, the anchor is the adjacent parent group being split at the surrounding level.

The action carries resize operations for both the external node and the anchor, each set to half of the anchor's current size. `computeInsertNode` does not mutate the drag item; `moveNode` applies both resize operations after inserting the node, making insertion and sizing one layout transaction.

Only `computeInsertNode` adds these operations. Existing `computeMoveNode` calls for tile-to-tile dragging remain unchanged.

## Data Flow

1. Hover computes the external insertion action and continues to show the existing half-space placeholder.
2. A valid drop creates the backend block.
3. The insertion action is recomputed against the latest layout state.
4. The new node and insertion anchor each receive half of the anchor's current size.
5. The move and resize operations commit together, then the new tile receives focus.

If the target disappears while the backend block is being created, the existing default-placement fallback remains unchanged.

## Testing

- Reproduce an `80`-size main pane receiving a right-side external tile and assert `40:40` while a `20`-size Files sidebar stays `20`.
- Cover top, right, bottom, and left equal splits.
- Cover an outer-edge insertion that splits the adjacent group's share without changing unrelated siblings.
- Verify ordinary tile move actions do not gain external resize operations.
- Run focused layout and block-drop tests, the complete Vitest suite, TypeScript validation, and diff checks.
- Package and install the macOS arm64 app, then verify its signature, architecture, bundle hash, and running process.

## Out of Scope

- Changing click-based default placement or sidebar widths.
- Rebalancing all panes in a row or column.
- Changing center swap, resize handles, invalid drop regions, or empty-layout behavior.
