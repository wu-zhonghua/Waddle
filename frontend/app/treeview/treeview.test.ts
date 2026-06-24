// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { buildVisibleRows, getTreeNodeClickAction, mergeInitialTreeNodes, TreeNodeData } from "@/app/treeview/treeview";
import { describe, expect, it } from "vitest";

function makeNodes(entries: TreeNodeData[]): Map<string, TreeNodeData> {
    return new Map(entries.map((entry) => [entry.id, entry]));
}

describe("treeview visible rows", () => {
    it("sorts directories before files and alphabetically", () => {
        const nodes = makeNodes([
            {
                id: "root",
                isDirectory: true,
                childrenStatus: "loaded",
                childrenIds: ["c", "a", "b"],
            },
            { id: "a", parentId: "root", isDirectory: false, label: "z-last.txt" },
            { id: "b", parentId: "root", isDirectory: true, label: "docs", childrenStatus: "loaded", childrenIds: [] },
            { id: "c", parentId: "root", isDirectory: false, label: "a-first.txt" },
        ]);
        const rows = buildVisibleRows(nodes, ["root"], new Set(["root"]));
        expect(rows.map((row) => row.id)).toEqual(["root", "b", "c", "a"]);
    });

    it("sorts sibling groups by metadata fields", () => {
        const nodes = makeNodes([
            {
                id: "root",
                isDirectory: true,
                childrenStatus: "loaded",
                childrenIds: ["large-file", "small-file", "large-dir", "small-dir"],
            },
            {
                id: "large-dir",
                parentId: "root",
                isDirectory: true,
                label: "large-dir",
                size: 300,
                childrenStatus: "loaded",
                childrenIds: [],
            },
            {
                id: "small-dir",
                parentId: "root",
                isDirectory: true,
                label: "small-dir",
                size: 100,
                childrenStatus: "loaded",
                childrenIds: [],
            },
            { id: "large-file", parentId: "root", isDirectory: false, label: "large.txt", size: 200 },
            { id: "small-file", parentId: "root", isDirectory: false, label: "small.txt", size: 50 },
        ]);
        const rows = buildVisibleRows(nodes, ["root"], new Set(["root"]), { field: "size", direction: "asc" });

        expect(rows.map((row) => row.id)).toEqual(["root", "small-dir", "large-dir", "small-file", "large-file"]);
    });

    it("renders loading and capped synthetic rows", () => {
        const nodes = makeNodes([
            { id: "root", isDirectory: true, childrenStatus: "loading" },
            {
                id: "dir",
                isDirectory: true,
                childrenStatus: "capped",
                childrenIds: ["f1"],
                capInfo: { max: 1 },
            },
            { id: "f1", parentId: "dir", isDirectory: false, label: "one.txt" },
        ]);
        const loadingRows = buildVisibleRows(nodes, ["root"], new Set(["root"]));
        expect(loadingRows.map((row) => row.kind)).toEqual(["node", "loading"]);

        const cappedRows = buildVisibleRows(nodes, ["dir"], new Set(["dir"]));
        expect(cappedRows.map((row) => row.kind)).toEqual(["node", "node", "capped"]);
    });

    it("preserves expanded directory children when root nodes refresh", () => {
        const currentNodes = makeNodes([
            {
                id: "/repo/src",
                label: "src",
                isDirectory: true,
                childrenStatus: "loaded",
                childrenIds: ["/repo/src/app.ts"],
            },
            {
                id: "/repo/src/app.ts",
                parentId: "/repo/src",
                label: "app.ts",
                isDirectory: false,
                size: 10,
            },
            {
                id: "/repo/README.md",
                label: "README.md",
                isDirectory: false,
                size: 20,
            },
        ]);
        const refreshedNodes: Record<string, TreeNodeData> = {
            "/repo/src": {
                id: "/repo/src",
                label: "src",
                isDirectory: true,
                childrenStatus: "unloaded",
                size: 99,
            },
            "/repo/README.md": {
                id: "/repo/README.md",
                label: "README.md",
                isDirectory: false,
                size: 30,
            },
            "/repo/new.txt": {
                id: "/repo/new.txt",
                label: "new.txt",
                isDirectory: false,
                size: 40,
            },
        };

        const mergedNodes = mergeInitialTreeNodes(currentNodes, refreshedNodes);
        const rows = buildVisibleRows(mergedNodes, ["/repo/src", "/repo/README.md", "/repo/new.txt"], new Set(["/repo/src"]));

        expect(mergedNodes.get("/repo/src")).toMatchObject({
            size: 99,
            childrenStatus: "loaded",
            childrenIds: ["/repo/src/app.ts"],
        });
        expect(rows.map((row) => row.id)).toEqual(["/repo/src", "/repo/src/app.ts", "/repo/new.txt", "/repo/README.md"]);
    });
});

describe("treeview row clicks", () => {
    it("toggles directory rows on a single click", () => {
        expect(
            getTreeNodeClickAction({
                id: "src",
                depth: 0,
                kind: "node",
                label: "src",
                isDirectory: true,
            })
        ).toBe("toggle");
    });

    it("opens parent directory rows on a single click", () => {
        expect(
            getTreeNodeClickAction({
                id: "/repo/..",
                depth: 0,
                kind: "node",
                label: "..",
                isDirectory: true,
                node: {
                    id: "/repo/..",
                    label: "..",
                    isDirectory: true,
                    clickAction: "open",
                },
            })
        ).toBe("open");
    });
});
