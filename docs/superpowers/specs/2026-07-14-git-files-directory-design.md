# Git Default Directory from Files Design

## Goal

When a Git block is created without an explicit project directory, default it to the directory shown by the most recently focused Files block. Files is the user's primary project navigator, so Git should open against the same project even when another block is currently focused.

## Current Behavior

`createBlock` calls `applyInheritedBlockLocation` with only the current layout focus. The inheritance helper first uses that focused block's location and then the first usable location in layout order. Consequently, opening Git while a Terminal is focused selects the Terminal working directory even when Files is showing the intended project.

The layout model already maintains a most-recent-first focus history for live layout nodes, but block location inheritance cannot currently inspect it.

## Approved Behavior

- A Git block with no explicit `cmd:cwd`, or with `cmd:cwd` equal to `~`, inherits the latest directory and connection from the most recently focused Files block.
- When multiple Files blocks exist, focus recency decides which one Git follows.
- If no Files block appears in focus history but Files blocks exist, use the first Files block in layout order. This provides deterministic behavior after restoring a layout before every Files block has been focused in the current session.
- If no Files block exists, retain the current fallback: use the focused block's usable location, otherwise the first usable location in layout order, and ultimately let Git default to `~` when no location is available.
- An explicitly configured Git project directory remains unchanged.
- Terminal, Files, Preview, Web, and explicit split behavior remain unchanged.

## Design

### Focus History Access

Expose a read-only snapshot of `LayoutModel`'s existing node focus history. Return a copied array ordered from most recently focused to least recently focused so callers cannot mutate layout state. Existing stale-node and duplicate cleanup remains owned by `LayoutModel`.

### Location Selection

Keep `applyInheritedBlockLocation` as the pure location-selection boundary. Extend its inputs with the ordered focus history supplied by `createBlock`.

For an inheriting Git block, location selection follows this order:

1. Walk focus history from newest to oldest and select the first node whose metadata represents a Files block with a usable location.
2. If none matches, walk the layout in its existing order and select the first Files location.
3. If no Files location exists, run the existing generic focused-location and layout-location fallback unchanged.

Once selected, copy both `file` to `cmd:cwd` and the Files `connection` value when the corresponding Git metadata is unset. This keeps local and remote project selection aligned.

Other block types continue using the existing generic inheritance order. The Git-specific priority must not alter Terminal or Files creation semantics.

### Data Flow

1. The user changes or focuses a Files block; its current directory is stored in that block's metadata and its node is recorded by the existing layout focus history.
2. The user creates Git from the Widgets panel.
3. `createBlock` reads a focus-history snapshot and the latest metadata for layout blocks.
4. `applyInheritedBlockLocation` resolves the preferred Files location and returns a copied Git block definition with inherited metadata.
5. The Git block is created and performs its existing status refresh using that directory and connection.

## Error and Fallback Handling

No new user-visible error state is required. Missing or stale focus-history entries are ignored through layout-node lookup. Files metadata without a usable directory or connection is skipped. If no usable inherited location is found, block creation proceeds with the original definition and Git's existing `~` default.

## Testing

Add focused unit coverage for:

- two Files blocks where Git chooses the most recently focused Files block while Terminal is currently focused;
- inheriting both directory and remote connection from the selected Files block;
- falling back to the first Files block when focus history contains no Files block;
- preserving the current focused-Terminal fallback when no Files block exists;
- preserving an explicitly configured Git directory;
- leaving existing Terminal and Files inheritance tests unchanged.

Run the targeted block-placement tests and the relevant layout-model tests, then run TypeScript type checking and formatting/diff checks required by the repository workflow.

## Scope

This change only affects the default location chosen when creating Git blocks. It does not synchronize Git continuously after creation, change Git panel placement, add configuration, or modify backend Git/RPC behavior.
