// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    canCommitGitStatus,
    formatGitRpcError,
    getGitFileBucket,
    groupGitFiles,
    isMissingGitRpcCommand,
    makeGitRpcRoute,
    makeGitReviewPrompt,
    normalizeGitProjectCwd,
} from "./git-model";

describe("getGitFileBucket", () => {
    it("classifies staged, unstaged, conflicted, and untracked files", () => {
        expect(getGitFileBucket({ path: "staged.txt", indexstatus: "M", worktreestatus: ".", status: "modified" })).toBe(
            "staged"
        );
        expect(getGitFileBucket({ path: "changed.txt", indexstatus: ".", worktreestatus: "M", status: "modified" })).toBe(
            "changes"
        );
        expect(getGitFileBucket({ path: "new.txt", indexstatus: "?", worktreestatus: "?", status: "untracked" })).toBe(
            "untracked"
        );
        expect(getGitFileBucket({ path: "conflict.txt", indexstatus: "U", worktreestatus: "U", status: "conflicted" })).toBe(
            "changes"
        );
    });
});

describe("groupGitFiles", () => {
    it("keeps file order inside vscode-style groups", () => {
        const grouped = groupGitFiles([
            { path: "b.txt", indexstatus: "M", worktreestatus: ".", status: "modified" },
            { path: "a.txt", indexstatus: ".", worktreestatus: "M", status: "modified" },
            { path: "c.txt", indexstatus: "?", worktreestatus: "?", status: "untracked" },
        ]);

        expect(grouped.staged.map((file) => file.path)).toEqual(["b.txt"]);
        expect(grouped.changes.map((file) => file.path)).toEqual(["a.txt"]);
        expect(grouped.untracked.map((file) => file.path)).toEqual(["c.txt"]);
    });
});

describe("canCommitGitStatus", () => {
    it("requires a message and at least one staged file", () => {
        const status: GitStatusData = {
            cwd: "/repo",
            root: "/repo",
            files: [{ path: "a.txt", indexstatus: "M", worktreestatus: ".", status: "modified" }],
            haschanges: true,
        };

        expect(canCommitGitStatus(status, "message")).toBe(true);
        expect(canCommitGitStatus(status, " ")).toBe(false);
        expect(canCommitGitStatus({ ...status, files: [{ path: "a.txt", indexstatus: ".", worktreestatus: "M", status: "modified" }] }, "message")).toBe(
            false
        );
        expect(canCommitGitStatus(null, "message")).toBe(false);
    });
});

describe("makeGitRpcRoute", () => {
    it("uses an empty route for local and a connection route for remote hosts", () => {
        expect(makeGitRpcRoute("local")).toBe("");
        expect(makeGitRpcRoute("")).toBe("");
        expect(makeGitRpcRoute("ssh:prod")).toBe("conn:ssh:prod");
    });
});

describe("normalizeGitProjectCwd", () => {
    it("keeps explicit paths and falls back to home for blank input", () => {
        expect(normalizeGitProjectCwd(" /repo/app ")).toBe("/repo/app");
        expect(normalizeGitProjectCwd(" ")).toBe("~");
        expect(normalizeGitProjectCwd(null)).toBe("~");
    });
});

describe("formatGitRpcError", () => {
    it("turns missing git rpc commands into a remote helper hint", () => {
        const message = 'Error: command "gitreviewdiff" not found';

        expect(isMissingGitRpcCommand(message)).toBe(true);
        expect(formatGitRpcError(message, "ssh:prod")).toContain("Remote helper on ssh:prod needs to be updated");
    });
});

describe("makeGitReviewPrompt", () => {
    it("includes the target base and project", () => {
        const prompt = makeGitReviewPrompt("origin/main", "/repo/app");

        expect(prompt).toContain("origin/main");
        expect(prompt).toContain("/repo/app");
        expect(prompt).toContain("correctness bugs");
    });
});
