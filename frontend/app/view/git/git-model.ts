// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { globalStore } from "@/app/store/jotaiStore";
import { createBlock } from "@/app/store/global";
import { makeORef } from "@/app/store/wos";
import { TabRpcClient } from "@/app/store/wshrpcutil";
import { isBlank, makeConnRoute, stringToBase64 } from "@/util/util";
import * as jotai from "jotai";
import { createRef } from "react";
import { GitView } from "./git";
import type { GitEnv } from "./gitenv";

export type GitFileBucket = "staged" | "changes" | "untracked";

export type GitFileGroups = Record<GitFileBucket, GitFileStatus[]>;

export type GitActionStatus = {
    message: string;
    isError: boolean;
};

function isUntrackedFile(file: GitFileStatus): boolean {
    return file?.status === "untracked" || file?.indexstatus === "?";
}

function isStagedFile(file: GitFileStatus): boolean {
    if (file?.status === "conflicted" || isUntrackedFile(file)) {
        return false;
    }
    return file?.indexstatus != null && file.indexstatus !== "." && file.indexstatus !== "";
}

export function getGitFileBucket(file: GitFileStatus): GitFileBucket {
    if (isUntrackedFile(file)) {
        return "untracked";
    }
    if (isStagedFile(file)) {
        return "staged";
    }
    return "changes";
}

export function groupGitFiles(files: GitFileStatus[]): GitFileGroups {
    const groups: GitFileGroups = {
        staged: [],
        changes: [],
        untracked: [],
    };
    (files ?? []).forEach((file) => {
        groups[getGitFileBucket(file)].push(file);
    });
    return groups;
}

export function canCommitGitStatus(status: GitStatusData, message: string): boolean {
    if (status == null || (message ?? "").trim() === "") {
        return false;
    }
    return groupGitFiles(status.files).staged.length > 0;
}

export function makeGitRpcRoute(conn: string): string {
    if (isBlank(conn) || conn === "local") {
        return "";
    }
    return makeConnRoute(conn);
}

export function normalizeGitProjectCwd(cwd: string): string {
    const trimmed = (cwd ?? "").trim();
    if (trimmed === "") {
        return "~";
    }
    return trimmed;
}

export function isMissingGitRpcCommand(message: string): boolean {
    return /command "git(status|diff|filediff|reviewdiff|stage|unstage|commit)" not found/.test(message ?? "");
}

export function formatGitRpcError(error: unknown, conn: string): string {
    const message = String(error);
    if (!isMissingGitRpcCommand(message)) {
        return message;
    }
    if (!isBlank(conn) && conn !== "local") {
        return `${message}\nRemote helper on ${conn} needs to be updated before Git commands can run.`;
    }
    return `${message}\nRestart Waddle so the local Git RPC commands are loaded.`;
}

export function makeGitRpcOpts(conn: string): RpcOpts {
    const route = makeGitRpcRoute(conn);
    if (route === "") {
        return undefined;
    }
    return { route };
}

export function makeGitReviewPrompt(base: string, cwd: string): string {
    return [
        `Review the attached git diff for changes against ${base || "main"}.`,
        `Project: ${cwd}`,
        "",
        "Look for correctness bugs, regressions, risky edge cases, broken user workflows, and missing tests.",
        "Prioritize actionable findings with file/line references when possible.",
        "If you do not find a real issue, say that clearly.",
    ].join("\n");
}

function getDefaultGitStatus(): GitStatusData {
    return {
        cwd: "",
        root: "",
        files: [],
        haschanges: false,
    };
}

export class GitViewModel implements ViewModel {
    viewType = "git";
    blockId: string;
    env: GitEnv;

    viewIcon = jotai.atom<string>("code-branch");
    viewName = jotai.atom<string>("Git");
    noPadding = jotai.atom<boolean>(true);
    manageConnection = jotai.atom<boolean>(true);
    filterOutNowsh = jotai.atom<boolean>(true);

    statusAtom: jotai.PrimitiveAtom<GitStatusData>;
    loadingAtom: jotai.PrimitiveAtom<boolean>;
    errorAtom: jotai.PrimitiveAtom<string>;
    selectedPathAtom: jotai.PrimitiveAtom<string>;
    selectedStagedAtom: jotai.PrimitiveAtom<boolean>;
    diffAtom: jotai.PrimitiveAtom<string>;
    diffLoadingAtom: jotai.PrimitiveAtom<boolean>;
    commitMessageAtom: jotai.PrimitiveAtom<string>;
    projectPathAtom: jotai.PrimitiveAtom<string>;
    actionStatusAtom: jotai.PrimitiveAtom<GitActionStatus>;
    reviewStatusAtom: jotai.PrimitiveAtom<GitActionStatus>;
    reviewLoadingAtom: jotai.PrimitiveAtom<boolean>;
    focusRef: React.RefObject<HTMLTextAreaElement>;

    connection: jotai.Atom<string>;
    cwdAtom: jotai.Atom<string>;
    connStatus: jotai.Atom<ConnStatus>;
    groupedFilesAtom: jotai.Atom<GitFileGroups>;
    canCommitAtom: jotai.Atom<boolean>;
    viewText: jotai.Atom<HeaderElem[]>;

    refreshEpoch = 0;

    constructor({ blockId, waveEnv }: ViewModelInitType) {
        this.blockId = blockId;
        this.env = waveEnv;
        this.statusAtom = jotai.atom<GitStatusData>(getDefaultGitStatus()) as jotai.PrimitiveAtom<GitStatusData>;
        this.loadingAtom = jotai.atom<boolean>(false);
        this.errorAtom = jotai.atom<string>(null) as jotai.PrimitiveAtom<string>;
        this.selectedPathAtom = jotai.atom<string>(null) as jotai.PrimitiveAtom<string>;
        this.selectedStagedAtom = jotai.atom<boolean>(false);
        this.diffAtom = jotai.atom<string>("");
        this.diffLoadingAtom = jotai.atom<boolean>(false);
        this.commitMessageAtom = jotai.atom<string>("");
        this.projectPathAtom = jotai.atom<string>("~");
        this.actionStatusAtom = jotai.atom<GitActionStatus>(null) as jotai.PrimitiveAtom<GitActionStatus>;
        this.reviewStatusAtom = jotai.atom<GitActionStatus>(null) as jotai.PrimitiveAtom<GitActionStatus>;
        this.reviewLoadingAtom = jotai.atom<boolean>(false);
        this.focusRef = createRef<HTMLTextAreaElement>();

        this.connection = jotai.atom((get) => {
            const connValue = get(this.env.getBlockMetaKeyAtom(blockId, "connection"));
            if (isBlank(connValue)) {
                return "local";
            }
            return connValue;
        });
        this.cwdAtom = jotai.atom((get) => {
            const cwd = get(this.env.getBlockMetaKeyAtom(blockId, "cmd:cwd"));
            if (isBlank(cwd)) {
                return "~";
            }
            return cwd;
        });
        this.connStatus = jotai.atom((get) => {
            const connName = get(this.env.getBlockMetaKeyAtom(blockId, "connection"));
            return get(this.env.getConnStatusAtom(connName));
        });
        this.groupedFilesAtom = jotai.atom((get) => groupGitFiles(get(this.statusAtom).files));
        this.canCommitAtom = jotai.atom((get) => canCommitGitStatus(get(this.statusAtom), get(this.commitMessageAtom)));
        this.viewText = jotai.atom((get) => [
            {
                elemtype: "text",
                text: get(this.cwdAtom),
                className: "preview-filename",
            },
            {
                elemtype: "iconbutton",
                icon: "refresh",
                title: "Refresh Git Status",
                iconSpin: get(this.loadingAtom),
                click: () => this.refresh(),
            },
        ]);

        globalStore.set(this.projectPathAtom, globalStore.get(this.cwdAtom));
        this.refresh();
    }

    get viewComponent(): ViewComponent {
        return GitView;
    }

    giveFocus(): boolean {
        this.focusRef.current?.focus();
        return this.focusRef.current != null;
    }

    async refresh(cwdOverride?: string): Promise<void> {
        const epoch = ++this.refreshEpoch;
        const cwd = normalizeGitProjectCwd(cwdOverride ?? globalStore.get(this.cwdAtom));
        const conn = globalStore.get(this.connection);
        globalStore.set(this.loadingAtom, true);
        globalStore.set(this.errorAtom, null);
        try {
            const status = await this.env.rpc.GitStatusCommand(TabRpcClient, { cwd }, makeGitRpcOpts(conn));
            if (epoch !== this.refreshEpoch) {
                return;
            }
            globalStore.set(this.statusAtom, status);
            globalStore.set(this.loadingAtom, false);
            const selectedPath = globalStore.get(this.selectedPathAtom);
            const selectedFile = status.files.find((file) => file.path === selectedPath);
            if (status.files.length === 0) {
                globalStore.set(this.selectedPathAtom, null);
                globalStore.set(this.selectedStagedAtom, false);
                globalStore.set(this.diffAtom, "");
                return;
            }
            if (selectedFile == null) {
                globalStore.set(this.selectedPathAtom, null);
                globalStore.set(this.selectedStagedAtom, false);
            }
        } catch (e) {
            if (epoch !== this.refreshEpoch) {
                return;
            }
            globalStore.set(this.loadingAtom, false);
            globalStore.set(this.errorAtom, formatGitRpcError(e, conn));
        }
    }

    async setProjectCwd(cwd: string): Promise<void> {
        const nextCwd = normalizeGitProjectCwd(cwd);
        globalStore.set(this.projectPathAtom, nextCwd);
        globalStore.set(this.actionStatusAtom, null);
        try {
            await this.env.rpc.SetMetaCommand(TabRpcClient, {
                oref: makeORef("block", this.blockId),
                meta: { "cmd:cwd": nextCwd },
            });
            await this.refresh(nextCwd);
        } catch (e) {
            const conn = globalStore.get(this.connection);
            globalStore.set(this.errorAtom, formatGitRpcError(e, conn));
        }
    }

    async updateRemoteHelper(): Promise<void> {
        const conn = globalStore.get(this.connection);
        if (isBlank(conn) || conn === "local") {
            return;
        }
        globalStore.set(this.actionStatusAtom, { message: `Updating remote helper on ${conn}...`, isError: false });
        try {
            await this.env.rpc.ConnReinstallWshCommand(
                TabRpcClient,
                { connname: conn, logblockid: this.blockId },
                { timeout: 120000 }
            );
            globalStore.set(this.actionStatusAtom, { message: `Restarting remote helper on ${conn}...`, isError: false });
            try {
                await this.env.rpc.ConnDisconnectCommand(TabRpcClient, conn, { timeout: 30000 });
            } catch {
                // The connection may already be down after reinstalling wsh.
            }
            await this.env.rpc.ConnConnectCommand(TabRpcClient, { host: conn, logblockid: this.blockId }, { timeout: 120000 });
            const routeReady = await this.env.rpc.WaitForRouteCommand(
                TabRpcClient,
                { routeid: makeGitRpcRoute(conn), waitms: 20000 },
                { timeout: 25000 }
            );
            if (!routeReady) {
                throw new Error(`remote helper route for ${conn} did not come back online`);
            }
            globalStore.set(this.actionStatusAtom, { message: `Updated and reconnected ${conn}`, isError: false });
            await this.refresh();
        } catch (e) {
            globalStore.set(this.actionStatusAtom, { message: formatGitRpcError(e, conn), isError: true });
        }
    }

    async selectFile(file: GitFileStatus, staged: boolean): Promise<void> {
        globalStore.set(this.selectedPathAtom, file.path);
        globalStore.set(this.selectedStagedAtom, staged);
        await this.openDiff(file, staged);
    }

    async openDiff(file: GitFileStatus, staged: boolean): Promise<void> {
        const cwd = normalizeGitProjectCwd(globalStore.get(this.cwdAtom));
        const conn = globalStore.get(this.connection);
        const meta = {
            view: "gitdiff",
            "cmd:cwd": cwd,
            "git:path": file.path,
            "git:origpath": file.origpath,
            "git:staged": staged,
        } as MetaType;
        if (!isBlank(conn) && conn !== "local") {
            meta.connection = conn;
        }
        await createBlock({ meta }, false, true);
    }

    async loadDiff(path: string, staged: boolean): Promise<void> {
        const cwd = globalStore.get(this.cwdAtom);
        const conn = globalStore.get(this.connection);
        globalStore.set(this.diffLoadingAtom, true);
        try {
            const response = await this.env.rpc.GitDiffCommand(TabRpcClient, { cwd, path, staged }, makeGitRpcOpts(conn));
            globalStore.set(this.diffAtom, response.diff);
        } catch (e) {
            globalStore.set(this.diffAtom, formatGitRpcError(e, conn));
        } finally {
            globalStore.set(this.diffLoadingAtom, false);
        }
    }

    async stage(paths: string[]): Promise<void> {
        const cwd = globalStore.get(this.cwdAtom);
        const conn = globalStore.get(this.connection);
        try {
            await this.env.rpc.GitStageCommand(TabRpcClient, { cwd, paths }, makeGitRpcOpts(conn));
            await this.refresh();
        } catch (e) {
            globalStore.set(this.actionStatusAtom, { message: formatGitRpcError(e, conn), isError: true });
        }
    }

    async unstage(paths: string[]): Promise<void> {
        const cwd = globalStore.get(this.cwdAtom);
        const conn = globalStore.get(this.connection);
        try {
            await this.env.rpc.GitUnstageCommand(TabRpcClient, { cwd, paths }, makeGitRpcOpts(conn));
            await this.refresh();
        } catch (e) {
            globalStore.set(this.actionStatusAtom, { message: formatGitRpcError(e, conn), isError: true });
        }
    }

    async commit(): Promise<void> {
        const status = globalStore.get(this.statusAtom);
        const message = globalStore.get(this.commitMessageAtom);
        if (!canCommitGitStatus(status, message)) {
            return;
        }
        const cwd = globalStore.get(this.cwdAtom);
        const conn = globalStore.get(this.connection);
        globalStore.set(this.actionStatusAtom, null);
        try {
            const result = await this.env.rpc.GitCommitCommand(TabRpcClient, { cwd, message }, makeGitRpcOpts(conn));
            globalStore.set(this.commitMessageAtom, "");
            globalStore.set(this.actionStatusAtom, { message: `Committed ${result.hash.slice(0, 7)}`, isError: false });
            await this.refresh();
        } catch (e) {
            globalStore.set(this.actionStatusAtom, { message: formatGitRpcError(e, conn), isError: true });
        }
    }

    async findIssues(): Promise<void> {
        const cwd = normalizeGitProjectCwd(globalStore.get(this.cwdAtom));
        const conn = globalStore.get(this.connection);
        globalStore.set(this.reviewLoadingAtom, true);
        globalStore.set(this.reviewStatusAtom, { message: "Preparing review diff...", isError: false });
        try {
            const response = await this.env.rpc.GitReviewDiffCommand(
                TabRpcClient,
                { cwd, base: "main" },
                makeGitRpcOpts(conn)
            );
            if ((response.diff ?? "").trim() === "") {
                globalStore.set(this.reviewStatusAtom, {
                    message: `No changes against ${response.base}`,
                    isError: false,
                });
                return;
            }
            const prompt = makeGitReviewPrompt(response.base, cwd);
            await this.env.rpc.WaddleAIAddContextCommand(TabRpcClient, {
                newchat: true,
                submit: true,
                text: prompt,
                files: [
                    {
                        name: `git-diff-${response.base.replace(/\//g, "-")}.patch`,
                        type: "text/x-diff",
                        size: new TextEncoder().encode(response.diff).length,
                        data64: stringToBase64(response.diff),
                    },
                ],
            });
            globalStore.set(this.reviewStatusAtom, {
                message: `Sent changes against ${response.base} to AI review`,
                isError: false,
            });
        } catch (e) {
            globalStore.set(this.reviewStatusAtom, { message: formatGitRpcError(e, conn), isError: true });
        } finally {
            globalStore.set(this.reviewLoadingAtom, false);
        }
    }
}
