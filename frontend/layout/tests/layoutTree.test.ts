// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { assert, expect, test } from "vitest";
import { findNode, newLayoutNode } from "../lib/layoutNode";
import {
    computeInsertNode,
    computeMoveNode,
    deleteNode,
    insertLeftSidebar,
    moveNode,
    splitHorizontal,
} from "../lib/layoutTree";
import {
    DropDirection,
    FlexDirection,
    LayoutTreeActionType,
    LayoutTreeComputeMoveNodeAction,
    LayoutTreeMoveNodeAction,
} from "../lib/types";
import { newLayoutTreeState } from "./model";

test("layoutTreeStateReducer - compute move", () => {
    const nodeA = newLayoutNode(undefined, undefined, undefined, { blockId: "nodeA" });
    const node1 = newLayoutNode(undefined, undefined, undefined, { blockId: "node1" });
    const node2 = newLayoutNode(undefined, undefined, undefined, { blockId: "node2" });
    const treeState = newLayoutTreeState(newLayoutNode(undefined, undefined, [nodeA, node1, node2]));
    assert(treeState.rootNode.children!.length === 3, "root should have three children");
    let pendingAction = computeMoveNode(treeState, {
        type: LayoutTreeActionType.ComputeMove,
        nodeId: treeState.rootNode.id,
        nodeToMoveId: node1.id,
        direction: DropDirection.Bottom,
    });
    const insertOperation = pendingAction as LayoutTreeMoveNodeAction;
    assert(insertOperation.node === node1, "insert operation node should equal node1");
    assert(!insertOperation.parentId, "insert operation parent should not be defined");
    assert(insertOperation.index === 1, "insert operation index should equal 1");
    assert(insertOperation.insertAtRoot, "insert operation insertAtRoot should be true");
    moveNode(treeState, insertOperation);
    assert(
        treeState.rootNode.data === undefined && treeState.rootNode.children!.length === 3,
        "root node should still have three children"
    );
    assert(treeState.rootNode.children![1].data!.blockId === "node1", "root's second child should be node1");

    pendingAction = computeMoveNode(treeState, {
        type: LayoutTreeActionType.ComputeMove,
        nodeId: node1.id,
        nodeToMoveId: node2.id,
        direction: DropDirection.Bottom,
    });
    const insertOperation2 = pendingAction as LayoutTreeMoveNodeAction;
    assert(insertOperation2.node === node2, "insert operation node should equal node2");
    assert(insertOperation2.parentId === node1.id, "insert operation parent id should be node1 id");
    assert(insertOperation2.index === 1, "insert operation index should equal 1");
    assert(!insertOperation2.insertAtRoot, "insert operation insertAtRoot should be false");
    moveNode(treeState, insertOperation2);
    assert(
        treeState.rootNode.data === undefined && (treeState.rootNode.children!.length as number) === 2,
        "root node should now have two children after node2 moved into node1"
    );
    assert(treeState.rootNode.children![1].children!.length === 2, "root's second child should now have two children");
});

test("computeMove - noop action", () => {
    const nodeToMove = newLayoutNode(undefined, undefined, undefined, { blockId: "nodeToMove" });
    const treeState = newLayoutTreeState(
        newLayoutNode(undefined, undefined, [
            nodeToMove,
            newLayoutNode(undefined, undefined, undefined, { blockId: "otherNode" }),
        ])
    );
    let moveAction: LayoutTreeComputeMoveNodeAction = {
        type: LayoutTreeActionType.ComputeMove,
        nodeId: treeState.rootNode.id,
        nodeToMoveId: nodeToMove.id,
        direction: DropDirection.Left,
    };
    let pendingAction = computeMoveNode(treeState, moveAction);

    assert(pendingAction === undefined, "inserting a node to the left of itself should not produce a pendingAction");

    moveAction = {
        type: LayoutTreeActionType.ComputeMove,
        nodeId: treeState.rootNode.id,
        nodeToMoveId: nodeToMove.id,
        direction: DropDirection.Right,
    };

    pendingAction = computeMoveNode(treeState, moveAction);
    assert(pendingAction === undefined, "inserting a node to the right of itself should not produce a pendingAction");

    const ordinaryMoveAction = computeMoveNode(treeState, {
        type: LayoutTreeActionType.ComputeMove,
        nodeId: treeState.rootNode.id,
        nodeToMoveId: nodeToMove.id,
        direction: DropDirection.Bottom,
    }) as LayoutTreeMoveNodeAction;
    expect(ordinaryMoveAction.resizeOperations).toBeUndefined();
});

test.each([
    [DropDirection.Top, 0],
    [DropDirection.OuterTop, 0],
    [DropDirection.Left, 0],
    [DropDirection.OuterLeft, 0],
    [DropDirection.Bottom, 1],
    [DropDirection.OuterBottom, 1],
    [DropDirection.Right, 1],
    [DropDirection.OuterRight, 1],
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

test.each([
    DropDirection.Top,
    DropDirection.Right,
    DropDirection.Bottom,
    DropDirection.Left,
])("computeInsertNode splits a target evenly for direction %s", (direction) => {
    const targetNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
    const newNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-widget" });
    const treeState = newLayoutTreeState(targetNode);
    const targetNodeId = targetNode.id;

    const action = computeInsertNode(treeState, targetNodeId, newNode, direction);
    moveNode(treeState, action);

    expect(findNode(treeState.rootNode, targetNodeId)?.size).toBe(40);
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

test("insertLeftSidebar wraps existing layout on the right", () => {
    const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
    const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "files" });
    const treeState = newLayoutTreeState(terminalNode);

    insertLeftSidebar(treeState, {
        type: LayoutTreeActionType.InsertLeftSidebar,
        node: filesNode,
        magnified: false,
        focused: true,
        sidebarSize: 20,
        mainSize: 80,
    });

    assert(treeState.rootNode.children?.[0].data?.blockId === "files", "files should be the left child");
    assert(treeState.rootNode.children?.[0].size === 20, "files should take one fifth of the row");
    assert(treeState.rootNode.children?.[1].data?.blockId === "terminal", "existing layout should move right");
    assert(treeState.rootNode.children?.[1].size === 80, "existing layout should take the remaining row width");
    assert(treeState.focusedNodeId === filesNode.id, "new files node should be focused");
});

test("splitHorizontal can split a target node without shrinking a left sidebar", () => {
    const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
    const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
    const previewNode = newLayoutNode(undefined, 40, undefined, { blockId: "preview" });
    const treeState = newLayoutTreeState(newLayoutNode(undefined, undefined, [filesNode, terminalNode]));

    splitHorizontal(treeState, {
        type: LayoutTreeActionType.SplitHorizontal,
        targetNodeId: terminalNode.id,
        newNode: previewNode,
        position: "after",
        focused: true,
        targetNodeSize: 40,
    });

    assert(treeState.rootNode.children?.[0].size === 20, "files should keep one fifth of the row");
    assert(treeState.rootNode.children?.[1].size === 40, "terminal should take half of the main row width");
    assert(treeState.rootNode.children?.[2].size === 40, "preview should take half of the main row width");
    assert(treeState.focusedNodeId === previewNode.id, "new preview node should be focused");
});

test("splitHorizontal can keep a left sidebar fixed while equalizing main panes", () => {
    const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
    const terminalNode = newLayoutNode(undefined, 40, undefined, { blockId: "terminal" });
    const previewNode = newLayoutNode(undefined, 40, undefined, { blockId: "preview" });
    const newPreviewNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-preview" });
    const treeState = newLayoutTreeState(newLayoutNode(undefined, undefined, [filesNode, terminalNode, previewNode]));

    splitHorizontal(treeState, {
        type: LayoutTreeActionType.SplitHorizontal,
        targetNodeId: previewNode.id,
        newNode: newPreviewNode,
        position: "after",
        focused: true,
        rebalanceRootRow: {
            fixedNodeId: filesNode.id,
            fixedSize: 20,
            remainingSize: 80,
        },
    });

    assert(treeState.rootNode.children?.[0].size === 20, "files should stay one fifth of the row");
    assert(treeState.rootNode.children?.[1].size === 80 / 3, "first main pane should be one third of the main area");
    assert(treeState.rootNode.children?.[2].size === 80 / 3, "second main pane should be one third of the main area");
    assert(treeState.rootNode.children?.[3].size === 80 / 3, "new pane should be one third of the main area");
});

test("deleteNode can keep a left sidebar fixed while equalizing remaining main panes", () => {
    const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
    const terminalNode = newLayoutNode(undefined, 40, undefined, { blockId: "terminal" });
    const previewNode = newLayoutNode(undefined, 40, undefined, { blockId: "preview" });
    const treeState = newLayoutTreeState(newLayoutNode(undefined, undefined, [filesNode, terminalNode, previewNode]));

    deleteNode(treeState, {
        type: LayoutTreeActionType.DeleteNode,
        nodeId: previewNode.id,
        rebalanceRootRow: {
            fixedNodeId: filesNode.id,
            fixedSize: 20,
            remainingSize: 80,
        },
    });

    assert(treeState.rootNode.children?.[0].size === 20, "files should stay one fifth of the row after close");
    assert(treeState.rootNode.children?.[1].size === 80, "remaining main pane should take the full main area");
});
