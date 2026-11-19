// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { createRequire } from "node:module";
import { describe, expect, it } from "vitest";

const require = createRequire(import.meta.url);
const ConfigPath = "./electron-builder.config.cjs";

function setOptionalEnv(key: string, value: string | undefined) {
    if (value == null) {
        delete process.env[key];
    } else {
        process.env[key] = value;
    }
}

function loadConfig(env: NodeJS.ProcessEnv = {}) {
    const oldGithubRepository = process.env.GITHUB_REPOSITORY;
    const oldWaddleUpdateGithubRepository = process.env.WADDLE_UPDATE_GITHUB_REPOSITORY;
    const oldWaddleUpdateUrl = process.env.WADDLE_UPDATE_URL;

    setOptionalEnv("GITHUB_REPOSITORY", env.GITHUB_REPOSITORY);
    setOptionalEnv("WADDLE_UPDATE_GITHUB_REPOSITORY", env.WADDLE_UPDATE_GITHUB_REPOSITORY);
    setOptionalEnv("WADDLE_UPDATE_URL", env.WADDLE_UPDATE_URL);
    delete require.cache[require.resolve(ConfigPath)];
    const config = require(ConfigPath);

    setOptionalEnv("GITHUB_REPOSITORY", oldGithubRepository);
    setOptionalEnv("WADDLE_UPDATE_GITHUB_REPOSITORY", oldWaddleUpdateGithubRepository);
    setOptionalEnv("WADDLE_UPDATE_URL", oldWaddleUpdateUrl);

    return config;
}

describe("electron-builder publish config", () => {
    it("uses the current GitHub repository as the update source in GitHub Actions", () => {
        const config = loadConfig({ GITHUB_REPOSITORY: "wu-zhonghua/Waddle" });

        expect(config.publish).toEqual({
            provider: "generic",
            url: "https://github.com/wu-zhonghua/Waddle/releases/latest/download",
        });
    });

    it("keeps the existing release bucket outside GitHub Actions", () => {
        const config = loadConfig();

        expect(config.publish).toEqual({
            provider: "generic",
            url: "https://dl.waddle.dev/releases-w2",
        });
    });
});
