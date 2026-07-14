// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { globalStore } from "@/app/store/jotaiStore";
import { makeMockWaddleEnv } from "@/preview/mock/mockwaveenv";
import { atom } from "jotai";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { getWebPreviewDisplayUrl, WebViewModel, WebViewPreviewFallback } from "./webview";

const TestDir = dirname(fileURLToPath(import.meta.url));

describe("web homepage default", () => {
    it("defaults to GitHub", () => {
        const settings = JSON.parse(
            readFileSync(join(TestDir, "../../../../pkg/wconfig/defaultconfig/settings.json"), "utf8")
        );

        expect(settings["web:defaulturl"]).toBe("https://github.com");
    });
});

describe("webview preview fallback", () => {
    it("shows the requested URL", () => {
        const markup = renderToStaticMarkup(<WebViewPreviewFallback url="https://waddle.dev/docs" />);

        expect(markup).toContain("electron webview unavailable");
        expect(markup).toContain("https://waddle.dev/docs");
    });

    it("falls back to about:blank when no URL is available", () => {
        expect(getWebPreviewDisplayUrl("")).toBe("about:blank");
        expect(getWebPreviewDisplayUrl(null)).toBe("about:blank");
    });

    it("uses the supplied env for homepage atoms and config updates", async () => {
        const blockId = "webview-env-block";
        const env = makeMockWaddleEnv({
            settings: {
                "web:defaulturl": "https://default.example",
            },
            mockWaddleObjs: {
                [`block:${blockId}`]: {
                    otype: "block",
                    oid: blockId,
                    version: 1,
                    meta: {
                        pinnedurl: "https://block.example",
                    },
                } as Block,
            },
        });
        const model = new WebViewModel({
            blockId,
            nodeModel: {
                isFocused: atom(true),
                focusNode: () => {},
            } as any,
            tabModel: {} as any,
            waveEnv: env,
        });

        expect(globalStore.get(model.homepageUrl)).toBe("https://block.example");

        await model.setHomepageUrl("https://global.example", "global");

        expect(globalStore.get(model.homepageUrl)).toBe("https://global.example");
        expect(globalStore.get(env.getSettingsKeyAtom("web:defaulturl"))).toBe("https://global.example");
        expect(globalStore.get(env.wos.getWaddleObjectAtom<Block>(`block:${blockId}`))?.meta?.pinnedurl).toBeUndefined();
    });
});
