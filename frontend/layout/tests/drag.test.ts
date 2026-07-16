// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { dispatchExternalTileDrop, isExternalTileDropDirection } from "../lib/drag";
import { newLayoutNode } from "../lib/layoutNode";
import { DropDirection } from "../lib/types";

describe("isExternalTileDropDirection", () => {
    it.each([
        DropDirection.Top,
        DropDirection.Right,
        DropDirection.Bottom,
        DropDirection.Left,
        DropDirection.OuterTop,
        DropDirection.OuterRight,
        DropDirection.OuterBottom,
        DropDirection.OuterLeft,
    ])("accepts edge direction %s", (direction) => {
        expect(isExternalTileDropDirection(direction)).toBe(true);
    });

    it("rejects center and missing directions", () => {
        expect(isExternalTileDropDirection(DropDirection.Center)).toBe(false);
        expect(isExternalTileDropDirection(undefined)).toBe(false);
    });
});

describe("dispatchExternalTileDrop", () => {
    it("invokes a valid edge drop exactly once", async () => {
        const onDrop = vi.fn().mockResolvedValue(undefined);
        const item = {
            node: newLayoutNode(undefined, undefined, undefined, { blockId: "external-widget" }),
            onDrop,
        };

        await expect(dispatchExternalTileDrop(item, "target", DropDirection.Left)).resolves.toBe(true);
        expect(onDrop).toHaveBeenCalledOnce();
        expect(onDrop).toHaveBeenCalledWith("target", DropDirection.Left);
    });

    it("does not invoke center drops", async () => {
        const onDrop = vi.fn().mockResolvedValue(undefined);
        const item = {
            node: newLayoutNode(undefined, undefined, undefined, { blockId: "external-widget" }),
            onDrop,
        };

        await expect(dispatchExternalTileDrop(item, "target", DropDirection.Center)).resolves.toBe(false);
        expect(onDrop).not.toHaveBeenCalled();
    });

    it("accepts the empty-layout target explicitly", async () => {
        const onDrop = vi.fn().mockResolvedValue(undefined);
        const item = {
            node: newLayoutNode(undefined, undefined, undefined, { blockId: "external-widget" }),
            onDrop,
        };

        await expect(dispatchExternalTileDrop(item, undefined, undefined, true)).resolves.toBe(true);
        expect(onDrop).toHaveBeenCalledOnce();
        expect(onDrop).toHaveBeenCalledWith(undefined, undefined);
    });
});
