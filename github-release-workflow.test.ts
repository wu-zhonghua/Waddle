// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("GitHub release workflow", () => {
    it("publishes releases with updater assets", () => {
        const workflow = readFileSync(".github/workflows/build-helper.yml", "utf8");

        expect(workflow).toContain("name: Waddle ${{ github.ref_name }} Release");
        expect(workflow).toContain("draft: false");
        expect(workflow).toContain("make/*.yml");
        expect(workflow).toContain("make/*.blockmap");
    });
});
