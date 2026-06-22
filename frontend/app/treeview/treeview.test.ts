// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { buildVisibleRows, getTreeNodeClickAction, TreeNodeData } from "@/app/treeview/treeview";
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
