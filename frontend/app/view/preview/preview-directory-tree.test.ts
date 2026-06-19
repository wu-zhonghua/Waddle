// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { fileInfoToTreeNode, filterDirectoryTreeEntries } from "./preview-directory-tree";

describe("directory tree helpers", () => {
    const entries: FileInfo[] = [
        { name: "..", path: "/", isdir: true, mimetype: "directory" },
        { name: ".env", path: "/repo/.env", isdir: false, mimetype: "text/plain" },
        { name: "src", path: "/repo/src", isdir: true, mimetype: "directory" },
        { name: "README.md", path: "/repo/README.md", isdir: false, mimetype: "text/markdown" },
    ];

    it("filters hidden files when hidden files are disabled", () => {
        expect(filterDirectoryTreeEntries(entries, false).map((entry) => entry.name)).toEqual(["..", "src", "README.md"]);
        expect(filterDirectoryTreeEntries(entries, true).map((entry) => entry.name)).toEqual(["..", ".env", "src", "README.md"]);
    });

    it("keeps the parent directory entry visible at the top of the tree", () => {
        expect(filterDirectoryTreeEntries(entries, false, "readme").map((entry) => entry.name)).toEqual(["..", "README.md"]);
        expect(fileInfoToTreeNode(entries[0], "/repo")).toMatchObject({
            label: "..",
            path: "/",
            isDirectory: true,
            childrenStatus: "loaded",
            clickAction: "open",
        });
    });

    it("maps FileInfo entries into tree nodes", () => {
        expect(fileInfoToTreeNode(entries[2], "/repo")).toMatchObject({
            id: "/repo/src",
            parentId: "/repo",
            label: "src",
            path: "/repo/src",
            isDirectory: true,
            mimeType: "directory",
            childrenStatus: "unloaded",
        });
        expect(fileInfoToTreeNode(entries[3], "/repo")).toMatchObject({
            id: "/repo/README.md",
            parentId: "/repo",
            label: "README.md",
            isDirectory: false,
            childrenStatus: "loaded",
        });
    });

    it("attaches configured icon visuals to tree nodes", () => {
        expect(
            fileInfoToTreeNode(entries[2], "/repo", {
                icon: "folder",
                iconColor: "var(--term-bright-blue)",
            })
        ).toMatchObject({
            icon: "folder",
            iconColor: "var(--term-bright-blue)",
        });
        expect(
            fileInfoToTreeNode(entries[3], "/repo", {
                icon: "markdown fa-brands",
                iconColor: "var(--term-green)",
            })
        ).toMatchObject({
            icon: "markdown fa-brands",
            iconColor: "var(--term-green)",
        });
    });
});
