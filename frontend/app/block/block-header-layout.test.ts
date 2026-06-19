// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { compileString } from "sass";
import { describe, expect, it } from "vitest";

const TestDir = dirname(fileURLToPath(import.meta.url));

function readBlockSource(filename: string): string {
    return readFileSync(join(TestDir, filename), "utf8");
}

describe("preview block header layout", () => {
    it("marks preview view headers with the two-row layout class", () => {
        const source = readBlockSource("blockframe-header.tsx");
        const frameSource = readBlockSource("blockframe.tsx");

        expect(source).toContain('const isPreviewHeader = metaView === "preview" && !useTermHeader;');
        expect(source).toContain('isPreviewHeader && "block-frame-preview-header"');
        expect(frameSource).toContain('"block-view-preview": metaView === "preview"');
    });

    it("gives preview paths their own full-width row", () => {
        const css = compileString(readBlockSource("block.scss")).css;

        expect(css).toContain(".block.block-frame-default.block-view-preview");
        expect(css).toContain(".block-frame-default-header.block-frame-preview-header");
        expect(css).toContain("min-height: var(--preview-header-height)");
        expect(css).toContain("max-height: var(--preview-header-height)");
        expect(css).toContain("flex-wrap: wrap");
        expect(css).toContain("flex-basis: 100%");
        expect(css).toContain("order: 2");
    });

    it("renders the preview path as an editable header input", () => {
        const source = readFileSync(join(TestDir, "../view/preview/preview-model.tsx"), "utf8");

        expect(source).toContain('elemtype: "input"');
        expect(source).toContain("className: \"preview-filename\"");
        expect(source).toContain("onKeyDown: this.handlePathInputKeyDown.bind(this)");
    });

    it("keeps a header button for the open path popup", () => {
        const source = readFileSync(join(TestDir, "../view/preview/preview-model.tsx"), "utf8");

        expect(source).toContain('icon: "folder-open"');
        expect(source).toContain("click: () => this.toggleOpenFileModal()");
    });

    it("renders directory view mode controls in the preview header", () => {
        const previewModelSource = readFileSync(join(TestDir, "../view/preview/preview-model.tsx"), "utf8");
        const directorySource = readFileSync(join(TestDir, "../view/preview/preview-directory.tsx"), "utf8");
        const css = compileString(readFileSync(join(TestDir, "../view/preview/directorypreview.scss"), "utf8")).css;

        expect(previewModelSource).toContain('className: "preview-directory-header-row"');
        expect(previewModelSource).toContain('className: "dir-view-mode-toggle"');
        expect(previewModelSource).toContain('className: "preview-path-controls"');
        expect(previewModelSource).toContain("setDirectoryViewMode");
        expect(directorySource).not.toContain('className="dir-preview-toolbar"');
        expect(css).toContain(".block-frame-div.preview-directory-header-row");
        expect(css).toContain(".block-frame-div.preview-path-controls");
        expect(css).toContain(".block-frame-div.dir-view-mode-toggle");
    });
});
