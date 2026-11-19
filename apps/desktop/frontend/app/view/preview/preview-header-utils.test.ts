// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { formatPreviewHeaderPath } from "./preview-header-utils";

describe("formatPreviewHeaderPath", () => {
    it("uses the full resolved path for the home directory", () => {
        expect(
            formatPreviewHeaderPath("~", {
                path: "~",
                dir: "/home",
                name: "zhwu",
            } as FileInfo)
        ).toBe("/home/zhwu");
    });

    it("uses the full resolved path for nested home-relative directories", () => {
        expect(
            formatPreviewHeaderPath("~/projects/waddle", {
                path: "~/projects/waddle",
                dir: "/home/zhwu/projects",
                name: "waddle",
            } as FileInfo)
        ).toBe("/home/zhwu/projects/waddle");
    });

    it("trims a trailing slash except for root", () => {
        expect(formatPreviewHeaderPath("/repo/project/", null)).toBe("/repo/project");
        expect(formatPreviewHeaderPath("/", null)).toBe("/");
    });
});
