// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { setKeyUtilPlatform } from "@/util/keyutil";
import { PlatformLinux, PlatformMacOS, PlatformWindows } from "@/util/platformutil";
import { afterEach, describe, expect, it } from "vitest";
import { getTerminalMainKeyAction } from "./term-keyutil";

function makeKeyEvent(overrides: Partial<WaddleKeyboardEvent>): WaddleKeyboardEvent {
    return {
        type: "keydown",
        alt: false,
        cmd: false,
        control: false,
        key: "",
        meta: false,
        option: false,
        shift: false,
        ...overrides,
    } as WaddleKeyboardEvent;
}

describe("getTerminalMainKeyAction", () => {
    afterEach(() => setKeyUtilPlatform(PlatformMacOS));

    it("maps macOS main-key shortcuts to terminal actions", () => {
        setKeyUtilPlatform(PlatformMacOS);

        expect(getTerminalMainKeyAction(makeKeyEvent({ cmd: true, meta: true, key: "End" }))).toBe("scrollbottom");
        expect(getTerminalMainKeyAction(makeKeyEvent({ cmd: true, meta: true, key: "Home" }))).toBe("scrolltop");
        expect(getTerminalMainKeyAction(makeKeyEvent({ cmd: true, meta: true, key: "ArrowLeft" }))).toBe("linebegin");
        expect(getTerminalMainKeyAction(makeKeyEvent({ cmd: true, meta: true, key: "ArrowRight" }))).toBe("lineend");
    });

    it("maps Linux and Windows main-key shortcuts to the same terminal actions", () => {
        for (const platform of [PlatformLinux, PlatformWindows] as NodeJS.Platform[]) {
            setKeyUtilPlatform(platform);

            expect(getTerminalMainKeyAction(makeKeyEvent({ alt: true, cmd: true, key: "End" }))).toBe("scrollbottom");
            expect(getTerminalMainKeyAction(makeKeyEvent({ alt: true, cmd: true, key: "Home" }))).toBe("scrolltop");
            expect(getTerminalMainKeyAction(makeKeyEvent({ alt: true, cmd: true, key: "ArrowLeft" }))).toBe(
                "linebegin"
            );
            expect(getTerminalMainKeyAction(makeKeyEvent({ alt: true, cmd: true, key: "ArrowRight" }))).toBe("lineend");
        }
    });
});
