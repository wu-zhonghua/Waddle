# Git Changes Tree Design

## Goal

Render the Git view's **Changes** and **Untracked** groups as collapsible directory trees while preserving the existing diff selection and staging workflows.

## Scope

- Convert repository-relative file paths in Changes and Untracked into directory and file nodes.
- Expand every directory by default and allow users to collapse directories afterward.
- Automatically expand newly discovered directories without reopening directories that a user already collapsed.
- Keep Merge Changes and Staged Changes as flat lists.
- Keep group-level Stage All actions.
- Add a directory-level Stage action that stages every changed or untracked descendant.
- Do not change Git RPCs, backend Git behavior, or Files tree behavior.

## Architecture

The shared `TreeView` gains two opt-in capabilities:

- `defaultExpandedIds` seeds expanded state and adds directory IDs that appear for the first time.
- `fitContent` sizes the tree to its visible rows so the Git group's existing scroll container remains responsible for scrolling.

Existing TreeView consumers retain their current defaults.

The Git model exposes a pure path adapter that builds a static tree for one source-control bucket. Node IDs are namespaced by bucket so Changes and Untracked cannot collide. The adapter returns root IDs, node data, directory IDs, file lookup data, and descendant file paths for directory actions.

## Interaction

- Directories sort before files; siblings sort alphabetically.
- Directory rows show a folder icon, disclosure control, and a `+` button.
- Clicking a directory toggles expansion. Clicking its `+` stages all descendant files.
- File rows show the leaf filename, status badge, and `+` button.
- Clicking a file opens its diff through the existing `selectFile` flow.
- Full paths, including rename source and destination, remain available in the row tooltip.
- Existing Git action status displays staging failures.

## State and Refresh

Tree node IDs remain stable across Git status refreshes. Existing expansion choices therefore survive refreshes. The TreeView records which default-expanded IDs it has already seen; only newly introduced directories are auto-expanded.

The externally selected file ID is derived from the Git model's selected path. Directory selection does not replace the selected diff.

## Verification

Implementation follows RED-GREEN TDD with focused tests for:

- nested path conversion and bucket isolation;
- descendant path collection for directory staging;
- default expansion and first-seen directory expansion;
- file selection and per-file staging wiring;
- unchanged flat rendering for Merge and Staged groups.

After focused tests, run the complete frontend test suite and TypeScript checks. Package the Apple Silicon app, install it locally, and verify the installed bundle. No Computer Use is required.
