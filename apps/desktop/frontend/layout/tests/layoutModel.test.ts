// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { LayoutModel } from "../lib/layoutModel";

vi.mock("electron", () => ({ net: { fetch: globalThis.fetch } }));

type LayoutModelFocusState = {
    focusedNodeIdStack: string[];
};

describe("LayoutModel focus history", () => {
    it("returns a copied most-recent-first node history", () => {
        const model = Object.create(LayoutModel.prototype) as LayoutModel;
        (model as unknown as LayoutModelFocusState).focusedNodeIdStack = ["recent", "older"];

        const history = model.focusedNodeIdHistory;
        (history as string[]).shift();

        expect(model.focusedNodeIdHistory).toEqual(["recent", "older"]);
    });
});
