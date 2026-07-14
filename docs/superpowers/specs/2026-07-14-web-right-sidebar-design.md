# Web Right Sidebar Design

## Goal

Open every newly created Web block at the far right of the root layout as a sidebar approximately 20% of the window width, instead of stacking it below an existing terminal. Change the built-in Web homepage default to `https://github.com`.

## Current Behavior

`getPlacementForBlockDef` classifies Web blocks as terminal-like. When a terminal already exists, `makeCreateBlockPlacementAction` therefore creates a vertical split and places the Web block below that terminal.

The built-in `web:defaulturl` setting currently points to `https://github.com/waddledev/waddle`. A block-level pinned homepage and a user-configured global homepage already override this built-in default.

## Design

### Placement Classification

Add a distinct `web` value to `CreateBlockPlacement`. Classify blocks with `meta.view === "web"` as Web placement while leaving terminal and Sysinfo classification unchanged.

Keeping Web placement distinct from Git placement avoids coupling the two view types even though both currently use the same visual layout.

### Layout Action

For Web placement, target the rightmost direct child of the root row and insert the new block after it with a horizontal split. Reuse the existing right-sidebar action shared with Git so both sidebars use the same 20% width calculation.

If the layout contains only one root block, reduce that block to 80% and assign 20% to Web. If the root is already a row, reduce the current rightmost root child by 20 percentage points and append Web as the final child. Existing Files, Terminal, Preview, Git, and default placement behavior remains unchanged.

If no target layout node exists, retain the existing no-action fallback.

### Homepage Default

Change the built-in `web:defaulturl` value in `pkg/wconfig/defaultconfig/settings.json` to `https://github.com`.

This changes the default for users who have not overridden the setting. Existing user configuration and block-level pinned homepages continue to take precedence and are not migrated or overwritten.

## Testing

Extend the focused block-placement tests to verify:

- Web block definitions resolve to `web` placement.
- A Web block opened beside a single pane is placed on the right at 20% width.
- A Web block opened in an existing root row is appended at the far right at 20% width.
- Terminal and Sysinfo placement behavior remains unchanged.

Run the focused Vitest suite for `frontend/app/store/block-placement.test.ts`. Validate the default settings JSON through the repository's existing configuration tests or JSON validation command if available.

## Scope

This change does not add a user-configurable Web width, migrate saved layouts, overwrite existing homepage preferences, or alter explicit split commands.
