// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { getUpdateStatusMessage } from "./updatebanner";

describe("update status banner", () => {
    it("only shows the Update action after an update is ready to install", () => {
        expect(getUpdateStatusMessage("up-to-date")).toBe(null);
        expect(getUpdateStatusMessage("checking")).toBe(null);
        expect(getUpdateStatusMessage("downloading")).toBe(null);
        expect(getUpdateStatusMessage("ready")).toBe("Update");
    });

    it("keeps the install-in-progress state visible after the user starts the update", () => {
        expect(getUpdateStatusMessage("installing")).toBe("Installing");
    });
});
