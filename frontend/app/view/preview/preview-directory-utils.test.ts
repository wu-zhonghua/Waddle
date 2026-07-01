// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { openDirectoryEntry, openNativePreviewForPlatform } from "./preview-directory-utils";

describe("openDirectoryEntry", () => {
    it("opens directories in the current preview block", async () => {
        const model = { goHistory: vi.fn() };
        const createBlock = vi.fn();

        await openDirectoryEntry(model, "/repo/src", true, "remote", createBlock);

        expect(model.goHistory).toHaveBeenCalledWith("/repo/src");
        expect(createBlock).not.toHaveBeenCalled();
    });

    it("opens files in a new preview block", async () => {
        const model = { goHistory: vi.fn() };
        const createBlock = vi.fn();

        await openDirectoryEntry(model, "/repo/README.md", false, "remote", createBlock);

        expect(model.goHistory).not.toHaveBeenCalled();
        expect(createBlock).toHaveBeenCalledWith(
            {
                meta: {
                    view: "preview",
                    file: "/repo/README.md",
                    connection: "remote",
                },
            },
            false,
            false,
            "preview"
        );
    });
});

describe("openNativePreviewForPlatform", () => {
    it("uses Quick Look on macOS", () => {
        const electron = {
            onQuicklook: vi.fn(),
            openNativePath: vi.fn(),
        };

        const handled = openNativePreviewForPlatform(electron, "darwin", "/repo/README.md");

        expect(handled).toBe(true);
        expect(electron.onQuicklook).toHaveBeenCalledWith("/repo/README.md");
        expect(electron.openNativePath).not.toHaveBeenCalled();
    });

    it("uses the native opener on Linux and Windows", () => {
        for (const platform of ["linux", "win32"] as NodeJS.Platform[]) {
            const electron = {
                onQuicklook: vi.fn(),
                openNativePath: vi.fn(),
            };

            const handled = openNativePreviewForPlatform(electron, platform, "/repo/README.md");

            expect(handled).toBe(true);
            expect(electron.onQuicklook).not.toHaveBeenCalled();
            expect(electron.openNativePath).toHaveBeenCalledWith("/repo/README.md");
        }
    });
});
