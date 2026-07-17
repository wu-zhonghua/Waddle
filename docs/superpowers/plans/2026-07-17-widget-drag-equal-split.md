# Widget Drag Equal-Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make widget edge drops commit the same 50:50 split shown by the drag placeholder without changing click placement or existing tile movement.

**Architecture:** Annotate external `Move` actions with optional resize operations for the actual insertion anchor and the external node. Resolve the anchor from the computed move's parent and index so inner edges split the target tile while outer edges split the adjacent group at the surrounding level; apply the move and both resizes in one reducer call.

**Tech Stack:** TypeScript 5.9, React layout tree, Vitest, Electron Builder, Task.

## Global Constraints

- Top, right, bottom, and left widget drops divide the target tile's previous space equally.
- Outer-edge widget drops remain at the surrounding level and divide the adjacent group's previous share equally.
- Unrelated siblings retain their sizes.
- Widget clicks, existing tile-to-tile moves, invalid drops, and empty-layout drops remain unchanged.
- Preserve every unrelated staged, unstaged, untracked, and shared-file hunk.
- Do not run direct `go build`; package only through the repository Task target.
- Install locally with rollback protection; do not push because direct push is not authorized.

---

### Task 1: External insertion sizing

**Files:**
- Modify: `frontend/layout/lib/types.ts`
- Modify: `frontend/layout/lib/layoutTree.ts`
- Modify: `frontend/layout/tests/layoutTree.test.ts`

**Interfaces:**
- Consumes: `computeMoveNode(layoutState, action, nodeToMoveOverride)`, `MoveOperation`, `ResizeNodeOperation`, `DropDirection`, and `moveNode(layoutState, action)`.
- Produces: `LayoutTreeMoveNodeAction.resizeOperations?: ResizeNodeOperation[]` populated only by `computeInsertNode`.

- [ ] **Step 1: Write failing equal-split tests**

Add `findNode` and `FlexDirection` to the existing test imports, then add tests that execute the real move reducer:

```ts
test.each([
    DropDirection.Top,
    DropDirection.Right,
    DropDirection.Bottom,
    DropDirection.Left,
])("computeInsertNode splits a target evenly for direction %s", (direction) => {
    const targetNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-widget" });
    const treeState = newLayoutTreeState(targetNode);

    const action = computeInsertNode(treeState, targetNode.id, newNode, direction);
    moveNode(treeState, action);

    expect(findNode(treeState.rootNode, targetNode.id)?.size).toBe(40);
    expect(findNode(treeState.rootNode, newNode.id)?.size).toBe(40);
});

test("computeInsertNode preserves a Files sidebar while splitting the main pane", () => {
    const filesNode = newLayoutNode(FlexDirection.Column, 20, undefined, { blockId: "files" });
    const terminalNode = newLayoutNode(FlexDirection.Column, 80, undefined, { blockId: "terminal" });
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-widget" });
    const treeState = newLayoutTreeState(newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]));

    const action = computeInsertNode(treeState, terminalNode.id, newNode, DropDirection.Right);
    moveNode(treeState, action);

    expect(filesNode.size).toBe(20);
    expect(terminalNode.size).toBe(40);
    expect(findNode(treeState.rootNode, newNode.id)?.size).toBe(40);
});

test("computeInsertNode splits the adjacent group for an outer edge", () => {
    const filesNode = newLayoutNode(FlexDirection.Column, 20, undefined, { blockId: "files" });
    const firstMainNode = newLayoutNode(FlexDirection.Row, 40, undefined, { blockId: "terminal" });
    const secondMainNode = newLayoutNode(FlexDirection.Row, 40, undefined, { blockId: "preview" });
    const mainGroup = newLayoutNode(FlexDirection.Column, 80, [firstMainNode, secondMainNode]);
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-widget" });
    const treeState = newLayoutTreeState(newLayoutNode(FlexDirection.Row, undefined, [filesNode, mainGroup]));

    const action = computeInsertNode(treeState, secondMainNode.id, newNode, DropDirection.OuterRight);
    moveNode(treeState, action);

    expect(filesNode.size).toBe(20);
    expect(mainGroup.size).toBe(40);
    expect(findNode(treeState.rootNode, newNode.id)?.size).toBe(40);
});
```

In the existing `computeMove - noop action` area, add a normal move case and assert `resizeOperations` remains absent:

```ts
const normalMove = computeMoveNode(treeState, {
    type: LayoutTreeActionType.ComputeMove,
    nodeId: treeState.rootNode.id,
    nodeToMoveId: nodeToMove.id,
    direction: DropDirection.Bottom,
}) as LayoutTreeMoveNodeAction;
expect(normalMove.resizeOperations).toBeUndefined();
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
npx vitest run frontend/layout/tests/layoutTree.test.ts
```

Expected: the new equal-split assertions fail because the target remains `80` and the external node remains `10`; existing layout-tree tests continue to run.

- [ ] **Step 3: Add resize operations to move actions**

Extend `MoveOperation` in `frontend/layout/lib/types.ts`:

```ts
export type MoveOperation = {
    index: number;
    parentId?: string;
    insertAtRoot?: boolean;
    node: LayoutNode;
    resizeOperations?: ResizeNodeOperation[];
};
```

In `frontend/layout/lib/layoutTree.ts`, add a private anchor resolver beside `computeInsertNode`:

```ts
function getExternalInsertAnchor(
    layoutState: LayoutTreeState,
    action: LayoutTreeMoveNodeAction,
    direction: DropDirection
): LayoutNode {
    const parent = action.insertAtRoot
        ? layoutState.rootNode
        : findNode(layoutState.rootNode, action.parentId);
    if (!parent?.children?.length) {
        return parent;
    }
    const insertsBefore =
        direction === DropDirection.Top ||
        direction === DropDirection.Left ||
        direction === DropDirection.OuterTop ||
        direction === DropDirection.OuterLeft;
    const anchorIndex = insertsBefore ? action.index : action.index - 1;
    return parent.children[Math.max(0, Math.min(anchorIndex, parent.children.length - 1))];
}
```

After `computeMoveNode` returns from `computeInsertNode`, attach equal resize operations without mutating the drag item:

```ts
const moveAction = computeMoveNode(
    layoutState,
    {
        type: LayoutTreeActionType.ComputeMove,
        nodeId: targetNodeId,
        nodeToMoveId: nodeToInsert.id,
        direction,
    },
    nodeToInsert
) as LayoutTreeMoveNodeAction | undefined;
if (moveAction == null) {
    return undefined;
}
const anchorNode = getExternalInsertAnchor(layoutState, moveAction, direction);
if (anchorNode == null) {
    return moveAction;
}
const splitSize = anchorNode.size / 2;
return {
    ...moveAction,
    resizeOperations: [
        { nodeId: anchorNode.id, size: splitSize },
        { nodeId: nodeToInsert.id, size: splitSize },
    ],
};
```

At the end of `moveNode`, after removing an old parent if present, apply the optional resize operations through the existing resize reducer:

```ts
if (action.resizeOperations?.length) {
    resizeNode(layoutState, {
        type: LayoutTreeActionType.ResizeNode,
        resizeOperations: action.resizeOperations,
    });
}
```

- [ ] **Step 4: Run focused verification and verify GREEN**

Run:

```bash
npx vitest run frontend/layout/tests/layoutTree.test.ts frontend/layout/tests/drag.test.ts frontend/app/store/block-drop.test.ts
task check:ts
git diff --check
```

Expected: all focused tests pass, TypeScript exits with code `0`, and diff check prints nothing.

- [ ] **Step 5: Commit the sizing fix**

```bash
git add frontend/layout/lib/types.ts frontend/layout/lib/layoutTree.ts frontend/layout/tests/layoutTree.test.ts
git diff --cached --name-only
git diff --cached --check
git commit -m "fix: split dragged widgets evenly"
```

Expected: only the three listed paths are committed.

---

### Task 2: Repository verification and local installation

**Files:**
- Verify: repository-wide source and tests
- Package: `make/mac-arm64/Waddle.app`
- Install: `/Applications/Waddle.app`

**Interfaces:**
- Consumes: the committed layout sizing change from Task 1 and the repository's `task package` target.
- Produces: a verified local macOS arm64 installation with recoverable previous application copies.

- [ ] **Step 1: Run repository-wide verification**

Run:

```bash
npx vitest run
task check:ts
git diff --check
git status --short --branch
```

Expected: every test passes, TypeScript exits with code `0`, diff check prints nothing, and only the user's preserved unrelated changes remain uncommitted.

- [ ] **Step 2: Package the current main worktree**

Run:

```bash
task package -- --mac --arm64 --dir
```

Expected: exit code `0` and `make/mac-arm64/Waddle.app` exists. If dependency download fails in the sandbox, rerun the same Task command with approved network access.

- [ ] **Step 3: Verify the package before installation**

Run:

```bash
codesign --verify --deep --strict --verbose=2 make/mac-arm64/Waddle.app
file make/mac-arm64/Waddle.app/Contents/MacOS/Waddle
shasum -a 256 make/mac-arm64/Waddle.app/Contents/Resources/app.asar
```

Expected: signature validation succeeds, the executable is arm64, and an `app.asar` SHA-256 is printed.

- [ ] **Step 4: Install with rollback protection**

Use unique paths under `/private/tmp` for a backup and the replaced application. Copy the existing `/Applications/Waddle.app`, stage the new bundle under `/Applications`, verify the staged signature and hash, stop the old `Waddle` process, then atomically move the staged bundle into place with an EXIT trap that restores the old bundle if the final move fails.

- [ ] **Step 5: Verify and launch the installed application**

Run signature, architecture, version, and SHA-256 checks against `/Applications/Waddle.app`; confirm the installed hash matches the package, launch it with `open /Applications/Waddle.app`, and confirm `pgrep -x Waddle` returns a PID.

Expected: every check succeeds and the installed Waddle process is running.
