// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { shouldSelectPreviewPathInputOnMouseUp } from "./preview-header-utils";

describe("preview path input selection", () => {
    it("selects the whole path after a single click gives the input focus", () => {
        expect(shouldSelectPreviewPathInputOnMouseUp(false, 8, 8)).toBe(true);
    });

    it("preserves a dragged text selection when the input first receives focus", () => {
        expect(shouldSelectPreviewPathInputOnMouseUp(false, 3, 12)).toBe(false);
    });

    it("does not select the whole path after a drag even if selection is still collapsed on mouseup", () => {
        expect(shouldSelectPreviewPathInputOnMouseUp(false, 8, 8, true)).toBe(false);
    });

    it("does not reselect the whole path when clicking an already focused input", () => {
        expect(shouldSelectPreviewPathInputOnMouseUp(true, 8, 8)).toBe(false);
    });
});
