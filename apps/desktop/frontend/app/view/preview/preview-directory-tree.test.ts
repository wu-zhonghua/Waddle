// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
    fileInfoToTreeNode,
    filterDirectoryTreeEntries,
    getDirectoryTreeSymlinkVisuals,
    getResizedDirectoryTreeColumnWidth,
    normalizeDirectoryViewMode,
} from "./preview-directory-tree";

const TestDir = dirname(fileURLToPath(import.meta.url));

describe("directory tree helpers", () => {
    const entries: FileInfo[] = [
        { name: "..", path: "/", isdir: true, mimetype: "directory" },
        { name: ".env", path: "/repo/.env", isdir: false, mimetype: "text/plain" },
        { name: "src", path: "/repo/src", isdir: true, mimetype: "directory" },
        { name: "README.md", path: "/repo/README.md", isdir: false, mimetype: "text/markdown" },
    ];

    it("defaults directory previews to tree mode", () => {
        expect(normalizeDirectoryViewMode(null)).toBe("tree");
        expect(normalizeDirectoryViewMode(undefined)).toBe("tree");
        expect(normalizeDirectoryViewMode("folder")).toBe("tree");
        expect(normalizeDirectoryViewMode("tree")).toBe("tree");
    });

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

    it("preserves file details for tree row metadata", () => {
        expect(
            fileInfoToTreeNode(
                {
                    name: "README.md",
                    path: "/repo/README.md",
                    isdir: false,
                    mimetype: "text/markdown",
                    modestr: "-rw-r--r--",
                    modtime: 1720000000000,
                    createtime: 1710000000000,
                    size: 2048,
                },
                "/repo"
            )
        ).toMatchObject({
            modeStr: "-rw-r--r--",
            modTime: 1720000000000,
            createTime: 1710000000000,
            size: 2048,
            mimeType: "text/markdown",
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

    it("marks directory symlinks as expandable link nodes", () => {
        const symlinkDir = {
            name: "current",
            path: "/repo/current",
            isdir: true,
            symlink: true,
            mimetype: "directory",
        } as FileInfo & { symlink: boolean };

        expect(fileInfoToTreeNode(symlinkDir, "/repo")).toMatchObject({
            isDirectory: true,
            isSymlink: true,
            childrenStatus: "unloaded",
        });
        expect(getDirectoryTreeSymlinkVisuals(symlinkDir)).toEqual({
            icon: "folder-tree",
            iconColor: "var(--term-bright-cyan)",
        });
    });

    it("resizes standalone tree columns within usable widths", () => {
        const bounds = { min: 48, max: 240 };

        expect(getResizedDirectoryTreeColumnWidth(76, 40, bounds)).toBe(116);
        expect(getResizedDirectoryTreeColumnWidth(76, -60, bounds)).toBe(48);
        expect(getResizedDirectoryTreeColumnWidth(76, 300, bounds)).toBe(240);
    });

    it("shows only modified time and size as tree detail columns", () => {
        const directorySource = readFileSync(join(TestDir, "preview-directory.tsx"), "utf8");
        const css = readFileSync(join(TestDir, "directorypreview.scss"), "utf8");

        expect(directorySource).toContain('{ field: "modified", label: "Modified" }');
        expect(directorySource).toContain('{ field: "size", label: "Size" }');
        expect(directorySource).not.toContain('{ field: "type", label: "Type" }');
        expect(directorySource).not.toContain('{ field: "created", label: "Created" }');
        expect(directorySource).not.toContain("dir-tree-detail-type");
        expect(directorySource).not.toContain("dir-tree-detail-created");
        expect(css).not.toContain("dir-tree-head-type");
        expect(css).not.toContain("dir-tree-head-created");
    });

    it("keeps tree detail columns aligned in compact layouts", () => {
        const directorySource = readFileSync(join(TestDir, "preview-directory.tsx"), "utf8");
        const css = readFileSync(join(TestDir, "directorypreview.scss"), "utf8");

        expect(css).toContain("--dir-tree-detail-columns");
        expect(directorySource).not.toContain("dir-tree-head-details");
        expect(css).not.toContain("dir-tree-head-details");
        expect(css).toContain("grid-template-columns: var(--dir-tree-detail-columns)");
        expect(css).toContain("--dir-tree-modified-track-width: var(--dir-tree-compact-modified-width);");
        expect(css).toContain("--dir-tree-detail-columns: minmax(");
        expect(css).toContain("minmax(var(--dir-tree-size-min-width), var(--dir-tree-size-width))");
        expect(css).toMatch(/\.dir-tree-head-modified\s*\{[\s\S]*?grid-column:\s*2/);
        expect(css).toMatch(/\.dir-tree-head-size\s*\{[\s\S]*?grid-column:\s*3/);
        expect(css).toMatch(/\.dir-tree-detail-modified\s*\{[\s\S]*?grid-column:\s*1/);
        expect(css).toMatch(/\.dir-tree-detail-size\s*\{[\s\S]*?grid-column:\s*2/);
    });

    it("opens modified and size at their minimum usable widths", () => {
        const css = readFileSync(join(TestDir, "directorypreview.scss"), "utf8");

        expect(css).toContain("--dir-tree-name-min-width: 300px;");
        expect(css).toContain("--dir-tree-name-width: 1fr;");
        expect(css).toContain("--dir-tree-compact-name-min-width: 190px;");
        expect(css).toContain("--dir-tree-compact-modified-width: 92px;");
        expect(css).toContain("--dir-tree-compact-size-width: 48px;");
        expect(css).toContain("--dir-tree-modified-width: 92px;");
        expect(css).toContain("--dir-tree-modified-track-width: var(--dir-tree-modified-width);");
        expect(css).toContain("--dir-tree-size-min-width: 48px;");
        expect(css).toContain("--dir-tree-size-width: 48px;");
        expect(css).toContain("minmax(var(--dir-tree-name-min-width), var(--dir-tree-name-width))");
        expect(css).toContain("--dir-tree-name-min-width: var(--dir-tree-compact-name-min-width);");
        expect(css).toContain("--dir-tree-size-width: var(--dir-tree-compact-size-width);");
        expect(css).not.toContain("@container (max-width: 360px)");
        expect(css).not.toMatch(
            /@container[\s\S]*?\.dir-tree-head-size,\s*\.dir-tree-detail-size\s*\{[\s\S]*?display:\s*none/
        );
        expect(css).not.toMatch(
            /@container \(max-width: 520px\)[\s\S]*?\.dir-tree-head-size,\s*\.dir-tree-detail-size\s*\{[\s\S]*?(display|padding-right)/
        );
    });

    it("exposes draggable resize handles for tree columns", () => {
        const directorySource = readFileSync(join(TestDir, "preview-directory.tsx"), "utf8");
        const css = readFileSync(join(TestDir, "directorypreview.scss"), "utf8");

        expect(directorySource).toContain("getResizedDirectoryTreeColumnWidth");
        expect(directorySource).not.toContain("DirectoryTreeResizeAdjacentColumn");
        expect(directorySource).not.toContain("getResizedDirectoryTreeColumnWidths");
        expect(directorySource).toContain("dir-tree-head-resize");
        expect(directorySource).toContain('resizeColumn="modified"');
        expect(directorySource).toContain('resizeColumn="size"');
        expect(directorySource).toContain("onPointerDown={(event) => onResizeStart(resizeColumn, event)}");
        expect(css).toContain("cursor: col-resize;");
        expect(css).toContain(".dir-tree-head-resize");
        expect(css).toContain("width: 18px;");
        expect(css).toContain("rgb(from var(--accent-color)");
        expect(css).toMatch(/\.dir-tree-head-size\s*\{[\s\S]*?\.dir-tree-head-resize\s*\{[\s\S]*?right:\s*0;/);
        expect(css).toMatch(/\.dir-tree-head-size\s*\{[\s\S]*?\.dir-tree-head-resize\s*\{[\s\S]*?width:\s*10px;/);
    });

    it("aligns tree headers and rows on the same column grid", () => {
        const css = readFileSync(join(TestDir, "directorypreview.scss"), "utf8");
        const treeViewSource = readFileSync(join(TestDir, "../../treeview/treeview.tsx"), "utf8");

        expect(treeViewSource).toContain("tree-node-row");
        expect(treeViewSource).toContain("tree-node-main");
        expect(css).toContain("--dir-tree-row-columns");
        expect(css).toContain("grid-template-columns: var(--dir-tree-row-columns)");
        expect(css).toMatch(/\.dir-tree-head,\s*\.dir-tree \.tree-node-row/);
        expect(css).toMatch(/\.dir-tree-head-name,\s*\.dir-tree \.tree-node-main/);
    });
});
