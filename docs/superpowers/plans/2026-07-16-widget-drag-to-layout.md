# Widget Drag-to-Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let every block-creating widget in the right sidebar create a normally sized block directly at a user-selected layout edge in one drag gesture.

**Architecture:** Add a generic external-tile React DnD contract to the layout package, refactor layout insertion so it can preview a node that is not yet in the tree, and let widget drag items defer block creation until a valid drop. Production and preview environments expose a create-at-layout-position operation that preserves current metadata inheritance and falls back to default placement if an asynchronous target disappears.

**Tech Stack:** TypeScript 5.9, React 19, React DnD 16 with the HTML5 backend, Jotai, Vitest, Electron Vite, Task.

## Global Constraints

- Existing widget click behavior, including configured magnification and default placement, must not change.
- Settings, Apps, and popup-menu entries remain click-only.
- Widget drags accept top, right, bottom, left, and outer-edge directions; the center is invalid.
- Canceling, dropping outside, and dropping in the center must not create a backend block.
- A valid drag creates a non-magnified block exactly once and focuses it at the previewed position.
- Empty layouts accept a widget as their sole full-size tile.
- Existing connection, working-directory, and Files-focused inheritance must remain active.
- Preserve all unrelated staged, unstaged, untracked, and shared-file hunks.
- Do not run direct `go build`; package with the repository Task target.
- Do not push; the user authorized local development and installation only.

---

### Task 1: External-node layout insertion

**Files:**
- Modify: `frontend/layout/lib/layoutTree.ts`
- Modify: `frontend/layout/tests/layoutTree.test.ts`
- Modify: `frontend/layout/index.ts`

**Interfaces:**
- Consumes: `LayoutTreeState`, `LayoutNode`, `DropDirection`, and the existing `LayoutTreeMoveNodeAction`.
- Produces: `computeInsertNode(layoutState: LayoutTreeState, targetNodeId: string, nodeToInsert: LayoutNode, direction: DropDirection): LayoutTreeMoveNodeAction | undefined`.

- [ ] **Step 1: Write failing external-node insertion tests**

Add `computeInsertNode` to the layout-tree imports, add `expect` to the Vitest imports, and add tests that construct one existing terminal leaf plus a new node that is not in the tree:

```ts
test.each([
    [DropDirection.Top, 0],
    [DropDirection.Left, 0],
    [DropDirection.Bottom, 1],
    [DropDirection.Right, 1],
])("computeInsertNode inserts an external node for direction %s", (direction, expectedIndex) => {
    const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-widget" });
    const treeState = newLayoutTreeState(terminalNode);

    const action = computeInsertNode(treeState, terminalNode.id, newNode, direction);

    expect(action).toMatchObject({
        type: LayoutTreeActionType.Move,
        node: newNode,
        index: expectedIndex,
    });
});

test("computeInsertNode rejects center and missing targets", () => {
    const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-widget" });
    const treeState = newLayoutTreeState(terminalNode);

    expect(computeInsertNode(treeState, terminalNode.id, newNode, DropDirection.Center)).toBeUndefined();
    expect(computeInsertNode(treeState, "missing", newNode, DropDirection.Left)).toBeUndefined();
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
npx vitest run frontend/layout/tests/layoutTree.test.ts
```

Expected: FAIL because `computeInsertNode` is not exported.

- [ ] **Step 3: Extract the insertion calculation**

Allow `computeMoveNode` to receive an optional already-resolved node, then add a public wrapper for external nodes:

```ts
export function computeInsertNode(
    layoutState: LayoutTreeState,
    targetNodeId: string,
    nodeToInsert: LayoutNode,
    direction: DropDirection
): LayoutTreeMoveNodeAction | undefined {
    if (targetNodeId == null || nodeToInsert == null || direction === DropDirection.Center) {
        return undefined;
    }
    return computeMoveNode(
        layoutState,
        {
            type: LayoutTreeActionType.ComputeMove,
            nodeId: targetNodeId,
            nodeToMoveId: nodeToInsert.id,
            direction,
        },
        nodeToInsert
    ) as LayoutTreeMoveNodeAction | undefined;
}
```

Change the existing function signature and source resolution with this diff:

```diff
-export function computeMoveNode(layoutState: LayoutTreeState, computeInsertAction: LayoutTreeComputeMoveNodeAction) {
+export function computeMoveNode(
+    layoutState: LayoutTreeState,
+    computeInsertAction: LayoutTreeComputeMoveNodeAction,
+    nodeToMoveOverride?: LayoutNode
+) {
     const rootNode = layoutState.rootNode;
     // existing validation and lazy lookups remain unchanged
-    const nodeToMove = findNode(rootNode, nodeToMoveId);
+    const nodeToMove = nodeToMoveOverride ?? findNode(rootNode, nodeToMoveId);
```

In the existing Left and Right cases, add the same root insertion handling already used by Top and Bottom:

```ts
if (isRoot) {
    newMoveOperation = {
        node: nodeToMove,
        index: direction === DropDirection.Left ? 0 : 1,
        insertAtRoot: true,
    };
}
```

Keep center swapping unchanged and export `computeInsertNode` from `frontend/layout/index.ts`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
npx vitest run frontend/layout/tests/layoutTree.test.ts
```

Expected: all layout-tree tests pass, including existing tile move and swap coverage.

- [ ] **Step 5: Commit the insertion helper**

```bash
git add frontend/layout/lib/layoutTree.ts frontend/layout/tests/layoutTree.test.ts frontend/layout/index.ts
git diff --cached --check
git commit -m "feat: compute external tile insertion"
```

Expected: only the three listed paths are committed.

---

### Task 2: Generic external tile drag targets

**Files:**
- Create: `frontend/layout/lib/drag.ts`
- Create: `frontend/layout/tests/drag.test.ts`
- Modify: `frontend/layout/lib/TileLayout.tsx`
- Modify: `frontend/layout/index.ts`
- Modify: `frontend/app/tab/tabcontent.tsx`

**Interfaces:**
- Consumes: `computeInsertNode` from Task 1 and the existing layout placeholder/reducer.
- Produces: `ExternalTileDragItemType`, `TileDragItemType`, `ExternalTileDragItem`, and `isExternalTileDropDirection`.

- [ ] **Step 1: Write failing direction-validation tests**

Create `frontend/layout/tests/drag.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { isExternalTileDropDirection } from "../lib/drag";
import { DropDirection } from "../lib/types";

describe("isExternalTileDropDirection", () => {
    it.each([
        DropDirection.Top,
        DropDirection.Right,
        DropDirection.Bottom,
        DropDirection.Left,
        DropDirection.OuterTop,
        DropDirection.OuterRight,
        DropDirection.OuterBottom,
        DropDirection.OuterLeft,
    ])("accepts edge direction %s", (direction) => {
        expect(isExternalTileDropDirection(direction)).toBe(true);
    });

    it("rejects center and missing directions", () => {
        expect(isExternalTileDropDirection(DropDirection.Center)).toBe(false);
        expect(isExternalTileDropDirection(undefined)).toBe(false);
    });
});
```

- [ ] **Step 2: Run the drag test and verify RED**

Run:

```bash
npx vitest run frontend/layout/tests/drag.test.ts
```

Expected: FAIL because `frontend/layout/lib/drag.ts` does not exist.

- [ ] **Step 3: Add the external drag contract**

Create `frontend/layout/lib/drag.ts`:

```ts
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

export { ExternalTileDragItemType, isExternalTileDropDirection, TileDragItemType };
export type { ExternalTileDragItem };
```

Re-export the constants and type from `frontend/layout/index.ts`.

- [ ] **Step 4: Extend TileLayout hover, drop, and cleanup**

In `frontend/layout/lib/TileLayout.tsx`:

- Replace the local tile item string with `TileDragItemType`.
- Treat both item types as active layout drags.
- Accept both item types in each overlay node.
- For external hover, compute a pending move with `computeInsertNode`; clear pending state for center or undefined directions.
- For external drop, clear the preview and invoke the item's callback only for a valid direction.
- Clear pending state when an active drag ends so Esc cannot leave a ghost placeholder.

The external branch follows this shape:

```ts
if (monitor.getItemType() === ExternalTileDragItemType) {
    const direction = getDropDirection(monitor);
    if (!isExternalTileDropDirection(direction)) {
        layoutModel.treeReducer({ type: LayoutTreeActionType.ClearPendingAction });
        return;
    }
    const dragItem = monitor.getItem<ExternalTileDragItem>();
    layoutModel.treeReducer({ type: LayoutTreeActionType.ClearPendingAction });
    fireAndForget(() => dragItem.onDrop(node.id, direction));
    return { handled: true };
}
```

Add an `EmptyLayoutDropTarget` that accepts only `ExternalTileDragItemType`, covers the empty display container, and calls `dragItem.onDrop()` once. Use `isOver` to apply a subtle `bg-accent/10` highlight.

- [ ] **Step 5: Keep TileLayout mounted for empty tabs**

In `frontend/app/tab/tabcontent.tsx`, remove the `tabData.blockids.length == 0` null branch. Render `TileLayout` for every loaded tab so the empty-layout target exists.

- [ ] **Step 6: Run layout tests and TypeScript verification**

Run:

```bash
npx vitest run frontend/layout/tests/drag.test.ts frontend/layout/tests/layoutTree.test.ts frontend/layout/tests/layoutModel.test.ts
task check:ts
```

Expected: all focused tests pass and TypeScript reports no errors.

- [ ] **Step 7: Commit generic drag support**

```bash
git add frontend/layout/lib/drag.ts frontend/layout/tests/drag.test.ts frontend/layout/lib/TileLayout.tsx frontend/layout/index.ts frontend/app/tab/tabcontent.tsx
git diff --cached --check
git commit -m "feat: accept external tile drags"
```

Expected: only the five listed paths plus the new test are committed.

---

### Task 3: Draggable widget creation

**Files:**
- Create: `frontend/app/workspace/widget-drag.ts`
- Create: `frontend/app/workspace/widget-drag.test.ts`
- Modify: `frontend/app/workspace/widgets.tsx`
- Modify: `frontend/app/store/global.ts`
- Modify: `frontend/app/waveenv/waveenv.ts`
- Modify: `frontend/app/waveenv/waveenvimpl.ts`
- Modify: `frontend/preview/mock/mockwaveenv.ts`
- Modify: `frontend/preview/previews/widgets.preview.tsx`

**Interfaces:**
- Consumes: `ExternalTileDragItem`, `ExternalTileDragItemType`, `computeInsertNode`, `createBlock`, and `getPlacementForBlockDef`.
- Produces: `createBlockAtLayoutPosition(blockDef: BlockDef, targetNodeId?: string, direction?: DropDirection, placement?: CreateBlockPlacement): Promise<string>` and `makeWidgetDragItem`.

- [ ] **Step 1: Write failing widget-drag item tests**

Create `frontend/app/workspace/widget-drag.test.ts`:

```ts
import { DropDirection } from "@/layout";
import { describe, expect, it, vi } from "vitest";
import { makeWidgetDragItem } from "./widget-drag";

describe("makeWidgetDragItem", () => {
    it("defers creation until drop and forwards explicit placement", async () => {
        const createAtPosition = vi.fn().mockResolvedValue("block-id");
        const widget = {
            label: "Files",
            blockdef: { meta: { view: "preview", file: "~" } },
        } as WidgetConfigType;

        const item = makeWidgetDragItem(widget, createAtPosition);

        expect(createAtPosition).not.toHaveBeenCalled();
        await item.onDrop("target-node", DropDirection.Left);
        expect(createAtPosition).toHaveBeenCalledWith(
            widget.blockdef,
            "target-node",
            DropDirection.Left,
            "files"
        );
    });
});
```

- [ ] **Step 2: Run the widget-drag test and verify RED**

Run:

```bash
npx vitest run frontend/app/workspace/widget-drag.test.ts
```

Expected: FAIL because `makeWidgetDragItem` does not exist.

- [ ] **Step 3: Implement deferred widget drag items**

Create `frontend/app/workspace/widget-drag.ts`:

```ts
import { CreateBlockPlacement, getPlacementForBlockDef } from "@/app/store/block-placement";
import { DropDirection, ExternalTileDragItem, newLayoutNode } from "@/layout";

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
        node: newLayoutNode(),
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
```

- [ ] **Step 4: Add create-at-position to production and preview environments**

Add `createBlockAtLayoutPosition` to `WaddleEnv`, `makeWaddleEnvImpl`, `MockEnv`, `mergeMockEnv`, and `makeMockWaddleEnv`.

In `frontend/app/store/global.ts`, share the existing inherited metadata resolution with `createBlock`, then implement:

```ts
async function createBlockAtLayoutPosition(
    blockDef: BlockDef,
    targetNodeId?: string,
    direction?: DropDirection,
    placement: CreateBlockPlacement = "default"
): Promise<string> {
    const layoutModel = getLayoutModelForStaticTab();
    const getBlockMeta = (existingBlockId: string) => {
        const block = globalStore.get(
            WOS.getWaddleObjectAtom<Block>(WOS.makeORef("block", existingBlockId))
        );
        return block?.meta;
    };
    const resolvedBlockDef = applyInheritedBlockLocation(
        blockDef,
        layoutModel.treeState?.rootNode,
        layoutModel.treeState?.focusedNodeId,
        placement,
        getBlockMeta,
        layoutModel.focusedNodeIdHistory
    );
    const blockId = await ObjectService.CreateBlock(resolvedBlockDef, { termsize: { rows: 25, cols: 80 } });
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
        placement === "default"
            ? null
            : makeCreateBlockPlacementAction(layoutModel.treeState?.rootNode, newNode, placement, getBlockMeta);
    layoutModel.treeReducer(
        placementAction ?? {
            type: LayoutTreeActionType.InsertNode,
            node: newNode,
            magnified: false,
            focused: true,
        }
    );
    return blockId;
}
```

The preview fallback delegates to its existing mock `createBlock` implementation because preview scenarios do not own a persistent tile layout.

- [ ] **Step 5: Make sidebar widgets draggable**

In `frontend/app/workspace/widgets.tsx`, add `createBlockAtLayoutPosition` to `WidgetsEnv`. Inside `Widget`, use `useDrag` with `ExternalTileDragItemType`, create the item lazily with `makeWidgetDragItem`, connect the returned ref through `Tooltip.divRef`, and apply `opacity-50` while dragging:

```ts
const dragRef = useRef<HTMLDivElement>(null);
const [{ isDragging }, drag] = useDrag(
    () => ({
        type: ExternalTileDragItemType,
        item: () => makeWidgetDragItem(widget, env.createBlockAtLayoutPosition),
        collect: (monitor) => ({ isDragging: monitor.isDragging() }),
    }),
    [widget, env]
);

useEffect(() => drag(dragRef), [drag]);
```

Keep `divOnClick={() => handleWidgetSelect(widget, env)}` unchanged. Do not add drag behavior to Apps or Settings.

- [ ] **Step 6: Keep the Widgets preview valid**

Wrap the `WidgetsPreview` scenarios in `DndProvider` with `HTML5Backend` so the new hook has a drag manager in preview mode.

- [ ] **Step 7: Run focused tests and TypeScript verification**

Run:

```bash
npx vitest run frontend/app/workspace/widget-drag.test.ts frontend/layout/tests/drag.test.ts frontend/layout/tests/layoutTree.test.ts frontend/app/store/block-placement.test.ts
task check:ts
```

Expected: all focused tests pass and TypeScript reports no errors. The widget test confirms no creation before a valid drop callback.

- [ ] **Step 8: Commit widget drag creation**

```bash
git add frontend/app/workspace/widget-drag.ts frontend/app/workspace/widget-drag.test.ts frontend/app/workspace/widgets.tsx frontend/app/store/global.ts frontend/app/waveenv/waveenv.ts frontend/app/waveenv/waveenvimpl.ts frontend/preview/mock/mockwaveenv.ts frontend/preview/previews/widgets.preview.tsx
git diff --cached --check
git commit -m "feat: create widgets at drag position"
```

Expected: only the eight listed feature paths are committed.

---

### Task 4: Repository verification and local installation

**Files:**
- Verify only: all paths modified in Tasks 1–3 plus preserved unrelated work.
- Package output: `make/mac-arm64/Waddle.app`.
- Install target: `/Applications/Waddle.app`.

**Interfaces:**
- Consumes: completed drag source, layout target, and create-at-position flow.
- Produces: verified source state and a rollback-protected local Apple Silicon installation.

- [ ] **Step 1: Run focused feature tests**

Run:

```bash
npx vitest run frontend/layout/tests/layoutTree.test.ts frontend/layout/tests/drag.test.ts frontend/layout/tests/layoutModel.test.ts frontend/app/workspace/widget-drag.test.ts frontend/app/store/block-placement.test.ts
```

Expected: every focused test passes with zero failures.

- [ ] **Step 2: Run the complete frontend suite**

Run:

```bash
npx vitest run
```

Expected: every frontend test file and test passes with zero failures.

- [ ] **Step 3: Run TypeScript verification**

Run:

```bash
task check:ts
```

Expected: exit code 0 with no TypeScript errors.

- [ ] **Step 4: Audit feature scope and preserved work**

Run:

```bash
git diff --check
git status --short --branch
git diff --cached --name-status
```

Expected: no whitespace errors, no staged content remains after the scoped commits, and all unrelated pre-existing modifications and untracked files remain present.

- [ ] **Step 5: Package the current Apple Silicon working tree**

Run:

```bash
task package -- --mac --arm64 --dir
```

Expected: exit code 0 and `make/mac-arm64/Waddle.app` contains a valid arm64 executable and code signature. The bundle intentionally includes the complete current working tree, including preserved user changes.

- [ ] **Step 6: Install with rollback protection**

Run with `/Applications` approval:

```bash
test ! -e /private/tmp/Waddle.app.codex-backup-widget-drag-20260716
test ! -e /private/tmp/Waddle.app.codex-replaced-widget-drag-20260716
test ! -e /Applications/.Waddle.app.codex-new-widget-drag-20260716
ditto /Applications/Waddle.app /private/tmp/Waddle.app.codex-backup-widget-drag-20260716
ditto make/mac-arm64/Waddle.app /Applications/.Waddle.app.codex-new-widget-drag-20260716
mv /Applications/Waddle.app /private/tmp/Waddle.app.codex-replaced-widget-drag-20260716
mv /Applications/.Waddle.app.codex-new-widget-drag-20260716 /Applications/Waddle.app
pkill -TERM -x Waddle || true
open /Applications/Waddle.app
```

Expected: the new bundle occupies `/Applications/Waddle.app`, the previous app is recoverable from `/private/tmp/Waddle.app.codex-backup-widget-drag-20260716`, and Waddle relaunches.

- [ ] **Step 7: Verify the installed bundle**

Run:

```bash
codesign --verify --deep --strict --verbose=2 /Applications/Waddle.app
file /Applications/Waddle.app/Contents/MacOS/Waddle
shasum -a 256 make/mac-arm64/Waddle.app/Contents/Resources/app.asar /Applications/Waddle.app/Contents/Resources/app.asar
```

Expected: signature verification succeeds, the executable reports arm64, and the packaged and installed `app.asar` hashes match.

- [ ] **Step 8: Final status audit**

Run:

```bash
git status --short --branch
git log -4 --oneline --decorate
```

Expected: scoped feature commits are present locally, no push occurred, and preserved unrelated work remains in its original staged/unstaged/untracked state.
