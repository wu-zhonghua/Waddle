// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const TestDir = dirname(fileURLToPath(import.meta.url));

describe("connection status overlay", () => {
    it("offers reconnect from the stalled connection overlay", () => {
        const source = readFileSync(join(TestDir, "connstatusoverlay.tsx"), "utf8");

        expect(source).toContain("onReconnect");
        expect(source).toContain("title=\"Reconnect\"");
        expect(source).toContain("onClick={onReconnect}");
        expect(source).toContain("onReconnect={handleTryReconnect}");
    });

    it("defaults new remote terminals to durable sessions", () => {
        const settings = JSON.parse(
            readFileSync(join(TestDir, "../../../pkg/wconfig/defaultconfig/settings.json"), "utf8")
        );

        expect(settings["term:durable"]).toBe(true);
    });
});
