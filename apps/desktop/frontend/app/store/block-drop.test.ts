// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { newLayoutNode } from "@/layout/lib/layoutNode";
import { DropDirection, LayoutTreeActionType, LayoutTreeState } from "@/layout/lib/types";
import { describe, expect, it, vi } from "vitest";
import { createBlockAtLayoutPositionWithDeps } from "./block-drop";

function makeLayoutModel(rootNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" })) {
    return {
        treeState: { rootNode, pendingBackendActions: [] } as LayoutTreeState,
        focusedNodeIdHistory: [] as readonly string[],
        treeReducer: vi.fn(),
        focusNode: vi.fn(),
    };
}

describe("createBlockAtLayoutPositionWithDeps", () => {
    it("creates and inserts a block at a valid edge", async () => {
        const layoutModel = makeLayoutModel();
        const createBlockObject = vi.fn().mockResolvedValue("new-block");

        await createBlockAtLayoutPositionWithDeps(
            { meta: { view: "web" } },
            layoutModel.treeState.rootNode.id,
            DropDirection.Right,
            "web",
            { layoutModel, createBlockObject, getBlockMeta: () => null }
        );

        expect(createBlockObject).toHaveBeenCalledOnce();
        expect(layoutModel.treeReducer).toHaveBeenCalledOnce();
        expect(layoutModel.treeReducer.mock.calls[0][0]).toMatchObject({
            type: LayoutTreeActionType.Move,
            node: { data: { blockId: "new-block" } },
        });
        expect(layoutModel.treeReducer.mock.calls[0][1]).toBe(false);
        expect(layoutModel.focusNode).toHaveBeenCalledOnce();
    });

    it("leaves the layout unchanged when backend creation fails", async () => {
        const layoutModel = makeLayoutModel();
        const createBlockObject = vi.fn().mockRejectedValue(new Error("create failed"));

        await expect(
            createBlockAtLayoutPositionWithDeps(
                { meta: { view: "web" } },
                layoutModel.treeState.rootNode.id,
                DropDirection.Right,
                "web",
                { layoutModel, createBlockObject, getBlockMeta: () => null }
            )
        ).rejects.toThrow("create failed");
        expect(layoutModel.treeReducer).not.toHaveBeenCalled();
        expect(layoutModel.focusNode).not.toHaveBeenCalled();
    });

    it("falls back to default placement when the target disappears", async () => {
        const layoutModel = makeLayoutModel();

        await createBlockAtLayoutPositionWithDeps(
            { meta: { view: "preview", file: "~" } },
            "missing-target",
            DropDirection.Left,
            "files",
            { layoutModel, createBlockObject: vi.fn().mockResolvedValue("files"), getBlockMeta: () => null }
        );

        expect(layoutModel.treeReducer.mock.calls[0][0]).toMatchObject({
            type: LayoutTreeActionType.InsertLeftSidebar,
            node: { data: { blockId: "files" } },
        });
    });

    it("inserts the first dropped block without sidebar sizing", async () => {
        const layoutModel = makeLayoutModel(undefined);
        layoutModel.treeState.rootNode = undefined;

        await createBlockAtLayoutPositionWithDeps(
            { meta: { view: "preview", file: "~" } },
            undefined,
            undefined,
            "files",
            { layoutModel, createBlockObject: vi.fn().mockResolvedValue("files"), getBlockMeta: () => null }
        );

        expect(layoutModel.treeReducer.mock.calls[0][0]).toMatchObject({
            type: LayoutTreeActionType.InsertNode,
            node: { data: { blockId: "files" } },
            magnified: false,
            focused: true,
        });
    });
});
