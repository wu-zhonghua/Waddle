// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { newLayoutNode } from "@/layout/lib/layoutNode";
import { splitHorizontal, splitVertical } from "@/layout/lib/layoutTree";
import {
    FlexDirection,
    LayoutTreeActionType,
    type LayoutTreeSplitHorizontalAction,
    type LayoutTreeSplitVerticalAction,
} from "@/layout/lib/types";
import { describe, expect, it } from "vitest";
import {
    applyInheritedBlockLocation,
    getPlacementForBlockDef,
    makeCreateBlockPlacementAction,
} from "./block-placement";

describe("makeCreateBlockPlacementAction", () => {
    const metas: Record<string, MetaType> = {
        files: { view: "preview", file: "~" },
        git: { view: "git", "cmd:cwd": "/repo" },
        terminal: { view: "term", controller: "shell" },
        "terminal-2": { view: "term", controller: "shell" },
        web: { view: "web" },
    };
    const getBlockMeta = (blockId: string) => metas[blockId];

    it("places the first files block as a left sidebar", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-files" });

        const action = makeCreateBlockPlacementAction(terminalNode, filesNode, "files", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.InsertLeftSidebar,
            sidebarSize: 20,
            mainSize: 80,
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

    it("opens git at the far right when files are already open", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const gitNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-git" });

        const action = makeCreateBlockPlacementAction(rootNode, gitNode, "git", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: terminalNode.id,
            newNode: gitNode,
            position: "after",
            focused: true,
            targetNodeSize: 60,
        });
        expect(gitNode.size).toBe(20);
    });

    it("opens the first git panel to the right of the current pane", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
        const gitNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-git" });

        const action = makeCreateBlockPlacementAction(terminalNode, gitNode, "git", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: terminalNode.id,
            newNode: gitNode,
            position: "after",
            focused: true,
            targetNodeSize: 80,
        });
        expect(gitNode.size).toBe(20);
    });

    it("opens git at the far right even if an older git panel is stacked with files", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "files" });
        const oldGitNode = newLayoutNode(undefined, undefined, undefined, { blockId: "git" });
        const filesStackNode = newLayoutNode(FlexDirection.Column, 20, [filesNode, oldGitNode]);
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesStackNode, terminalNode]);
        const gitNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-git" });

        const action = makeCreateBlockPlacementAction(rootNode, gitNode, "git", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: terminalNode.id,
            newNode: gitNode,
            position: "after",
            focused: true,
            targetNodeSize: 60,
        });
        expect(gitNode.size).toBe(20);
    });

    it("opens web at the far right when files are already open", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const webNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-web" });

        const action = makeCreateBlockPlacementAction(rootNode, webNode, "web", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: terminalNode.id,
            newNode: webNode,
            position: "after",
            focused: true,
            targetNodeSize: 60,
        });
        expect(webNode.size).toBe(20);
    });

    it("opens the first web panel to the right of the current pane", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "terminal" });
        const webNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-web" });

        const action = makeCreateBlockPlacementAction(terminalNode, webNode, "web", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: terminalNode.id,
            newNode: webNode,
            position: "after",
            focused: true,
            targetNodeSize: 80,
        });
        expect(webNode.size).toBe(20);
    });

    it("opens web to the right of the entire vertically stacked root", () => {
        const firstTerminal = newLayoutNode(undefined, 50, undefined, { blockId: "terminal" });
        const secondTerminal = newLayoutNode(undefined, 50, undefined, { blockId: "terminal-2" });
        const rootNode = newLayoutNode(FlexDirection.Column, undefined, [firstTerminal, secondTerminal]);
        const webNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-web" });
        const treeState = { rootNode, pendingBackendActions: [] };

        const action = makeCreateBlockPlacementAction(rootNode, webNode, "web", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: rootNode.id,
            targetNodeSize: 80,
            newNode: webNode,
            position: "after",
            focused: true,
        });

        splitHorizontal(treeState, action as LayoutTreeSplitHorizontalAction);

        expect(treeState.rootNode.flexDirection).toBe(FlexDirection.Row);
        expect(treeState.rootNode.children?.[0]).toBe(rootNode);
        expect(treeState.rootNode.children?.[0].size).toBe(80);
        expect(treeState.rootNode.children?.[1]).toBe(webNode);
        expect(treeState.rootNode.children?.[1].size).toBe(20);
    });

    it("stacks another web block inside the existing right sidebar", () => {
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        const existingWebNode = newLayoutNode(undefined, 20, undefined, { blockId: "web" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [terminalNode, existingWebNode]);
        const webNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-web" });
        const treeState = { rootNode, pendingBackendActions: [] };

        const action = makeCreateBlockPlacementAction(rootNode, webNode, "web", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitVertical,
            targetNodeId: existingWebNode.id,
            newNode: webNode,
            position: "after",
            focused: true,
        });

        splitVertical(treeState, action as LayoutTreeSplitVerticalAction);

        expect(treeState.rootNode.children).toHaveLength(2);
        expect(treeState.rootNode.children?.[0].size).toBe(80);
        expect(treeState.rootNode.children?.[1].flexDirection).toBe(FlexDirection.Column);
        expect(treeState.rootNode.children?.[1].size).toBe(20);
        expect(treeState.rootNode.children?.[1].children?.every((node) => node.size > 0)).toBe(true);
    });

    it("normalizes the root row when the main pane has no explicit size", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const terminalNode = newLayoutNode(undefined, 80, undefined, { blockId: "terminal" });
        terminalNode.size = undefined;
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const webNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-web" });
        const treeState = { rootNode, pendingBackendActions: [] };

        const action = makeCreateBlockPlacementAction(rootNode, webNode, "web", getBlockMeta);

        splitHorizontal(treeState, action as LayoutTreeSplitHorizontalAction);

        expect(treeState.rootNode.children?.map((node) => node.size)).toEqual([20, 60, 20]);
        expect(treeState.rootNode.children?.reduce((total, node) => total + node.size, 0)).toBe(100);
    });

    it("preserves explicit pane widths when adding the right sidebar", () => {
        const filesNode = newLayoutNode(undefined, 20, undefined, { blockId: "files" });
        const firstTerminal = newLayoutNode(undefined, 40, undefined, { blockId: "terminal" });
        const secondTerminal = newLayoutNode(undefined, 40, undefined, { blockId: "terminal-2" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, firstTerminal, secondTerminal]);
        const webNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-web" });
        const treeState = { rootNode, pendingBackendActions: [] };

        const action = makeCreateBlockPlacementAction(rootNode, webNode, "web", getBlockMeta);

        splitHorizontal(treeState, action as LayoutTreeSplitHorizontalAction);

        expect(treeState.rootNode.children?.map((node) => node.size)).toEqual([20, 40, 20, 20]);
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
        expect(newTermNode.size).toBe(80);
    });

    it("keeps a lone files block at the sidebar width when opening the first terminal", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "files" });
        const newTermNode = newLayoutNode(undefined, undefined, undefined, { blockId: "new-terminal" });
        const treeState = { rootNode: filesNode, pendingBackendActions: [] };

        const action = makeCreateBlockPlacementAction(filesNode, newTermNode, "terminal", getBlockMeta);

        expect(action).toMatchObject({
            type: LayoutTreeActionType.SplitHorizontal,
            targetNodeId: filesNode.id,
            targetNodeSize: 20,
            newNode: newTermNode,
            position: "after",
            focused: true,
        });

        splitHorizontal(treeState, action as LayoutTreeSplitHorizontalAction);

        expect(treeState.rootNode.children?.[0].size).toBe(20);
        expect(treeState.rootNode.children?.[1].size).toBe(80);
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
        expect(newPreviewNode.size).toBe(80);
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
        olderFiles: { view: "preview", file: "/srv/older", connection: "ssh:older" },
        recentFiles: { view: "preview", file: "/srv/recent", connection: "ssh:recent" },
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

    it("opens new git panels on the focused terminal connection and directory", () => {
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteTerminal" });
        const blockDef: BlockDef = { meta: { view: "git" } };

        const inherited = applyInheritedBlockLocation(blockDef, terminalNode, terminalNode.id, "git", getBlockMeta);

        expect(inherited.meta).toMatchObject({
            view: "git",
            connection: "ssh:web",
            "cmd:cwd": "/var/www",
        });
    });

    it("opens git on the most recently focused files directory", () => {
        const olderFilesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "olderFiles" });
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteTerminal" });
        const recentFilesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "recentFiles" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [olderFilesNode, terminalNode, recentFilesNode]);
        const blockDef: BlockDef = { meta: { view: "git" } };

        const inherited = applyInheritedBlockLocation(blockDef, rootNode, terminalNode.id, "git", getBlockMeta, [
            terminalNode.id,
            recentFilesNode.id,
            olderFilesNode.id,
        ]);

        expect(inherited.meta).toMatchObject({
            view: "git",
            connection: "ssh:recent",
            "cmd:cwd": "/srv/recent",
        });
    });

    it("falls back to the first files block when files are absent from focus history", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteFiles" });
        const terminalNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteTerminal" });
        const rootNode = newLayoutNode(FlexDirection.Row, undefined, [filesNode, terminalNode]);
        const blockDef: BlockDef = { meta: { view: "git" } };

        const inherited = applyInheritedBlockLocation(blockDef, rootNode, terminalNode.id, "git", getBlockMeta, [
            terminalNode.id,
        ]);

        expect(inherited.meta).toMatchObject({
            connection: "ssh:prod",
            "cmd:cwd": "/srv/app",
        });
    });

    it("does not override an explicit git directory", () => {
        const filesNode = newLayoutNode(undefined, undefined, undefined, { blockId: "remoteFiles" });
        const blockDef: BlockDef = {
            meta: {
                view: "git",
                connection: "ssh:manual",
                "cmd:cwd": "/opt/manual",
            },
        };

        const inherited = applyInheritedBlockLocation(blockDef, filesNode, filesNode.id, "git", getBlockMeta, [
            filesNode.id,
        ]);

        expect(inherited).toBe(blockDef);
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
        expect(getPlacementForBlockDef({ meta: { view: "sysinfo" } })).toBe("terminal");
    });

    it("opens web panels in web placement", () => {
        expect(getPlacementForBlockDef({ meta: { view: "web" } })).toBe("web");
    });

    it("keeps file previews in the files placement", () => {
        expect(getPlacementForBlockDef({ meta: { view: "preview", file: "~" } })).toBe("files");
    });

    it("uses default placement for other views", () => {
        expect(getPlacementForBlockDef({ meta: { view: "launcher" } })).toBe("default");
    });

    it("opens git panels in git placement", () => {
        expect(getPlacementForBlockDef({ meta: { view: "git" } })).toBe("git");
    });
});
