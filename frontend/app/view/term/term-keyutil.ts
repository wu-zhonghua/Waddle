// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import * as keyutil from "@/util/keyutil";

export type TerminalMainKeyAction = "scrollbottom" | "scrolltop" | "linebegin" | "lineend";

export function getTerminalMainKeyAction(waveEvent: WaddleKeyboardEvent): TerminalMainKeyAction {
    if (keyutil.checkKeyPressed(waveEvent, "Cmd:End")) {
        return "scrollbottom";
    }
    if (keyutil.checkKeyPressed(waveEvent, "Cmd:Home")) {
        return "scrolltop";
    }
    if (keyutil.checkKeyPressed(waveEvent, "Cmd:ArrowLeft")) {
        return "linebegin";
    }
    if (keyutil.checkKeyPressed(waveEvent, "Cmd:ArrowRight")) {
        return "lineend";
    }
    return null;
}
