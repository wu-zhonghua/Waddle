// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { DropDirection } from "@/layout/lib/types";
import { describe, expect, it, vi } from "vitest";
import { makeWidgetDragItem } from "./widget-drag";

describe("makeWidgetDragItem", () => {
    it("defers creation until drop and forwards explicit placement", async () => {
        const createAtPosition = vi.fn().mockResolvedValue("block-id");
        const widget = {
            label: "Files",
            blockdef: { meta: { view: "preview", file: "~" } },
        } as WidgetConfigType;

        const item = makeWidgetDragItem(widget, createAtPosition);

        expect(createAtPosition).not.toHaveBeenCalled();
        await item.onDrop("target-node", DropDirection.Left);
        expect(createAtPosition).toHaveBeenCalledOnce();
        expect(createAtPosition).toHaveBeenCalledWith(
            widget.blockdef,
            "target-node",
            DropDirection.Left,
            "files"
        );
    });

    it("supports dropping into an empty layout", async () => {
        const createAtPosition = vi.fn().mockResolvedValue("block-id");
        const widget = {
            label: "Terminal",
            blockdef: { meta: { view: "term", controller: "shell" } },
        } as WidgetConfigType;

        const item = makeWidgetDragItem(widget, createAtPosition);
        await item.onDrop();

        expect(createAtPosition).toHaveBeenCalledWith(widget.blockdef, undefined, undefined, "terminal");
    });
});
