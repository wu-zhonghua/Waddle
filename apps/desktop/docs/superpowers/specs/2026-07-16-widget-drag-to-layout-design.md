# Widget Drag-to-Layout Design

## Goal

Let users drag any right-sidebar widget directly into a chosen layout position so they can create and arrange a Terminal, Files, Web, Git, or custom widget in one gesture.

## Behavior

- Keep the existing click behavior unchanged, including default placement and magnification.
- Make every sidebar entry that directly creates a block draggable. Settings, Apps, and other menu-only controls remain click-only.
- Starting a drag does not create a backend block.
- While a widget is over the tile layout, show the existing layout placeholder for valid top, right, bottom, left, and outer-edge insertion positions.
- Treat the center region of an existing tile as invalid for widget drags. It remains available for the existing tile-to-tile swap interaction.
- Dropping on a valid position creates the block, inserts it at the previewed position, and focuses it.
- Dropping into an empty layout creates the widget as the sole full-size tile.
- Dropping outside the layout, dropping over an invalid center region, or canceling the drag creates nothing.
- A custom widget configured to open magnified still opens magnified when clicked. Dragging it creates a normal tile because a magnified block has no stable layout position.
- Preserve the existing connection, working-directory, and Files-focused inheritance rules when creating the dragged block.

## Architecture

### Widget Drag Source

Add a dedicated React DnD item type for uncreated widgets. A drag item carries the widget's block definition and the placement category derived from that definition. The sidebar `Widget` component remains responsible for click creation and becomes the drag source for the new item type.

The drag item is immutable for the duration of a gesture. It does not contain a block ID or mutate application layout state.

### Layout Drop Target

Extend the tile-layout drop layer to accept both existing tile items and uncreated widget items. Existing tile drags continue through the current move/swap path without behavior changes.

For widget hover events, the layout determines the drop direction using the existing geometry rules. Center and undefined directions clear any widget placeholder. Valid edge directions produce a pending insertion operation containing a temporary layout node used only for preview geometry.

The empty display container acts as a widget drop target only when the layout has no leaves.

### External-Node Insertion

Extract or add a pure layout-tree helper that computes an insertion operation for a node that is not yet present in the tree. Existing tile movement can continue resolving its source node before calling the shared insertion logic. Widget dragging supplies the temporary preview node directly.

This separation keeps backend creation out of hover handling and makes edge placement independently testable.

### Drop Commit

When a valid widget drop occurs, pass the widget definition and drop target specification to a create-at-drop-position operation. That operation:

1. Applies the existing inherited connection and directory metadata.
2. Creates the backend block.
3. Builds the real layout node using the returned block ID.
4. Recomputes the insertion against the latest layout state using the saved target node and direction.
5. Commits the insertion, focuses the new tile, and clears the temporary placeholder.

Recomputing after block creation avoids committing stale preview geometry if the layout changes while the asynchronous request is in flight.

## State and Visual Feedback

- The global layout drag state recognizes widget drags so placeholders and drag cleanup behave consistently with tile drags.
- The sidebar widget uses its existing icon and label as the drag preview and may reduce opacity while dragging.
- The cursor indicates that invalid center and out-of-layout positions cannot accept the widget.
- A drag-end cleanup always clears pending widget insertion state, regardless of whether a drop succeeded.

## Error Handling

- A backend creation failure clears the placeholder and leaves the layout unchanged. The existing RPC error reporting path remains responsible for surfacing the failure.
- If the saved target tile disappears before block creation finishes, insert the successfully created block using its existing default placement rule rather than losing an already-created backend block.
- Ignore duplicate drop callbacks after the first accepted drop in a gesture.
- Cleanup is idempotent so canceled drags, invalid drops, and component unmounts cannot leave a ghost placeholder.

## Testing

- Unit-test external-node insertion for top, right, bottom, left, and outer-edge directions.
- Verify center and undefined directions do not create a widget insertion operation.
- Verify an empty layout accepts a widget as its root node.
- Verify a valid drop creates exactly one backend block and commits the real node at the previewed location.
- Verify canceling, dropping outside, and dropping in the center create no backend block and clear pending state.
- Verify backend failures leave the layout unchanged, and a missing target after creation falls back to normal placement.
- Verify click creation remains unchanged, including configured magnification.
- Verify drag creation is non-magnified and retains connection and working-directory inheritance.
- Run focused Vitest suites and the repository's relevant TypeScript validation.

## Out of Scope

- Dragging Settings, Apps, or items inside their popup menus.
- Changing existing tile-to-tile drag, center-swap, or resize behavior.
- Adding touch-specific long-press behavior beyond what the current React DnD backend supports.
- Changing default click placement rules.
