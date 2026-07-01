// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const TestDir = dirname(fileURLToPath(import.meta.url));

function readTabSource(filename: string): string {
    return readFileSync(join(TestDir, filename), "utf8");
}

describe("tab bar app version", () => {
    it("shows the current Waddle version in both tab bar layouts", () => {
        const tabbarSource = readTabSource("tabbar.tsx");
        const vtabbarSource = readTabSource("vtabbar.tsx");

        expect(tabbarSource).toContain("AppVersionBadge");
        expect(tabbarSource).toContain("fullConfig?.version");
        expect(tabbarSource).toContain("<AppVersionBadge version={appVersion} />");
        expect(vtabbarSource).toContain("<AppVersionBadge version={appVersion} />");
    });
});
