// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { newLayoutNode } from "@/layout/lib/layoutNode";
import { FlexDirection, LayoutTreeActionType } from "@/layout/lib/types";
import { applyInheritedBlockLocation, getPlacementForBlockDef, makeCreateBlockPlacementAction } from "./block-placement";

describe("makeCreateBlockPlacementAction", () => {
    const metas: Record<string, MetaType> = {
        files: { view: "preview", file: "~" },
        terminal: { view: "term", controller: "shell" },
    };
    const getBlockMeta = (blockId: string) => metas[blockId];

    it("places the first files block as a left sidebar", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-files" });

        const action = makeCreateBlockPlacementAction(terminalNode, filesNode, "files", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.InsertLeftSidebar,
            sidebarSize: 21,
            mainSize: 79,
            focused: true,
        });
    });

    it("stacks files below an existing files block", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const newFilesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-files" });

        const action = makeCreateBlockPlacementAction(rootNode, newFilesNode, "files", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitVertical,
            targetNodeId: filesNode.id,
            newNode: newFilesNode,
            position: "after",
            focused: true,
        });
        expect(newFilesNode.size).toBeCloseTo(10);
    });

    it("stacks terminals below an existing terminal when files are already open", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const newTermNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-terminal" });

        const action = makeCreateBlockPlacementAction(rootNode, newTermNode, "terminal", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitVertical,
            targetNodeId: terminalNode.id,
            newNode: newTermNode,
            position: "after",
            focused: true,
        });
        expect(newTermNode.size).toBeCloseTo(40);
    });

    it("places the first terminal to the right of an existing files block", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "files" });
        const newTermNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-terminal" });

        const action = makeCreateBlockPlacementAction(filesNode, newTermNode, "terminal", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: filesNode.id,
            newNode: newTermNode,
            position: "after",
            focused: true,
        });
        expect(newTermNode.size).toBe(79);
    });

    it("opens a file preview to the right of a lone files block", () => {
        const filesNode = newLayoutNode(undefined, 21, undefined, { blockId: "files" });
        const newPreviewNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-preview" });

        const action = makeCreateBlockPlacementAction(filesNode, newPreviewNode, "preview", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: filesNode.id,
            newNode: newPreviewNode,
            position: "after",
            focused: true,
        });
        expect(newPreviewNode.size).toBe(79);
    });

    it("opens a file preview at the far right using the original Wave split size", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const newPreviewNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-preview" });

        const action = makeCreateBlockPlacementAction(rootNode, newPreviewNode, "preview", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: terminalNode.id,
            newNode: newPreviewNode,
            position: "after",
            focused: true,
            rebalanceRootRow: {
                fixedNodeId: filesNode.id,
                fixedSize: 20,
                remainingSize: 80,
            },
        });
    });

    it("equalizes all main panes when opening another file preview", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, 40, undefined, { blockId: "terminal" });
        const previewNode = newLayoutNode(undefined, 40, undefined, { blockId: "preview" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode, previewNode]);
        const newPreviewNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-preview" });

        const action = makeCreateBlockPlacementAction(rootNode, newPreviewNode, "preview", getBlockMeta);

        expect(action).toMatchObject({
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
    });
});

describe("applyInheritedBlockLocation", () => {
    const metas: Record<string, MetaType> = {
        remoteFiles: { view: "preview", file: "/srv/app", connection: "ssh:prod" },
        remoteTerminal: { view: "term", controller: "shell", "cmd:cwd": "/var/www", connection: "ssh:web" },
    };
    const getBlockMeta = (blockId: string) => metas[blockId];

    it("opens new terminals on the focused files connection and directory", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteFiles" });
        const blockDef: BlockDef = { meta: { view: "term", controller: "shell" } };

        const inherited = applyInheritedBlockLocation(blockDef, filesNode, filesNode.id, "terminal", getBlockMeta);

        expect(inherited.meta).toMatchObject({
            view: "term",
            controller: "shell",
            connection: "ssh:prod",
            "cmd:cwd": "/srv/app",
        });
        expect(blockDef.meta).not.toHaveProperty("connection");
    });

    it("opens new files on the focused terminal connection and directory", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteTerminal" });
        const blockDef: BlockDef = { meta: { view: "preview", file: "~" } };

        const inherited = applyInheritedBlockLocation(blockDef, terminalNode, terminalNode.id, "files", getBlockMeta);

        expect(inherited.meta).toMatchObject({
            view: "preview",
            file: "/var/www",
            connection: "ssh:web",
        });
        expect(blockDef.meta.file).toBe("~");
    });

    it("does not override explicit terminal location metadata", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteFiles" });
        const blockDef: BlockDef = {
            meta: {
                view: "term",
                controller: "shell",
                connection: "ssh:staging",
                "cmd:cwd": "/opt/current",
            },
        };

        const inherited = applyInheritedBlockLocation(blockDef, filesNode, filesNode.id, "terminal", getBlockMeta);

        expect(inherited.meta).toMatchObject({
            connection: "ssh:staging",
            "cmd:cwd": "/opt/current",
        });
    });

    it("does not override explicit files location metadata", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteTerminal" });
        const blockDef: BlockDef = {
            meta: {
                view: "preview",
                file: "/tmp/manual",
                connection: "ssh:manual",
            },
        };

        const inherited = applyInheritedBlockLocation(blockDef, terminalNode, terminalNode.id, "files", getBlockMeta);

        expect(inherited.meta).toMatchObject({
            file: "/tmp/manual",
            connection: "ssh:manual",
        });
    });
});

describe("getPlacementForBlockDef", () => {
    it("opens terminal-like widgets with terminal placement", () => {
        expect(getPlacementForBlockDef({ meta: { view: "term", controller: "shell" } })).toBe("terminal");
        expect(getPlacementForBlockDef({ meta: { view: "web" } })).toBe("terminal");
        expect(getPlacementForBlockDef({ meta: { view: "sysinfo" } })).toBe("terminal");
    });

    it("keeps file previews in the files placement", () => {
        expect(getPlacementForBlockDef({ meta: { view: "preview", file: "~" } })).toBe("files");
    });

    it("uses default placement for other views", () => {
        expect(getPlacementForBlockDef({ meta: { view: "launcher" } })).toBe("default");
    });
});
