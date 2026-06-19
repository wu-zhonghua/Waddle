// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { assert, test } from "vitest";
import { newLayoutNode } from "../lib/layoutNode";
import { computeMoveNode, insertLeftSidebar, moveNode, splitHorizontal } from "../lib/layoutTree";
import {
    DropDirection,
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
