// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { globalStore } from "@/app/store/jotaiStore";
import { TabRpcClient } from "@/app/store/wshrpcutil";
import { isBlank } from "@/util/util";
import * as jotai from "jotai";
import { formatGitRpcError, makeGitRpcOpts, normalizeGitProjectCwd } from "../git/git-model";
import { GitDiffView } from "./gitdiff";
import type { GitDiffEnv } from "./gitdiffenv";

export type GitDiffData = {
    original: string;
    modified: string;
    fileName: string;
};

export class GitDiffViewModel implements ViewModel {
    viewType = "gitdiff";
    blockId: string;
    env: GitDiffEnv;

    viewIcon = jotai.atom<string>("code-compare");
    viewName = jotai.atom<string>("Git Diff");
    noPadding = jotai.atom<boolean>(true);
    manageConnection = jotai.atom<boolean>(true);
    filterOutNowsh = jotai.atom<boolean>(true);

    diffDataAtom: jotai.PrimitiveAtom<GitDiffData>;
    loadingAtom: jotai.PrimitiveAtom<boolean>;
    errorAtom: jotai.PrimitiveAtom<string>;

    connection: jotai.Atom<string>;
    cwdAtom: jotai.Atom<string>;
    pathAtom: jotai.Atom<string>;
    origPathAtom: jotai.Atom<string>;
    stagedAtom: jotai.Atom<boolean>;
    viewText: jotai.Atom<HeaderElem[]>;

    refreshEpoch = 0;

    constructor({ blockId, waveEnv }: ViewModelInitType) {
        this.blockId = blockId;
        this.env = waveEnv;
        this.diffDataAtom = jotai.atom<GitDiffData>(null) as jotai.PrimitiveAtom<GitDiffData>;
        this.loadingAtom = jotai.atom<boolean>(false);
        this.errorAtom = jotai.atom<string>(null) as jotai.PrimitiveAtom<string>;

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
        this.pathAtom = this.env.getBlockMetaKeyAtom(blockId, "git:path");
        this.origPathAtom = this.env.getBlockMetaKeyAtom(blockId, "git:origpath");
        this.stagedAtom = this.env.getBlockMetaKeyAtom(blockId, "git:staged");
        this.viewText = jotai.atom((get) => [
            {
                elemtype: "text",
                text: get(this.pathAtom) || "Git Diff",
                className: "preview-filename",
            },
            {
                elemtype: "iconbutton",
                icon: "refresh",
                title: "Refresh Diff",
                iconSpin: get(this.loadingAtom),
                click: () => this.refresh(),
            },
        ]);

        this.refresh();
    }

    get viewComponent(): ViewComponent {
        return GitDiffView;
    }

    async refresh(): Promise<void> {
        const epoch = ++this.refreshEpoch;
        const cwd = normalizeGitProjectCwd(globalStore.get(this.cwdAtom));
        const conn = globalStore.get(this.connection);
        const path = globalStore.get(this.pathAtom);
        const origPath = globalStore.get(this.origPathAtom);
        const staged = globalStore.get(this.stagedAtom) ?? false;
        if (isBlank(path)) {
            globalStore.set(this.errorAtom, "Missing git file path");
            globalStore.set(this.loadingAtom, false);
            return;
        }
        globalStore.set(this.loadingAtom, true);
        globalStore.set(this.errorAtom, null);
        try {
            const response = await this.env.rpc.GitFileDiffCommand(
                TabRpcClient,
                { cwd, path, origpath: origPath, staged },
                makeGitRpcOpts(conn)
            );
            if (epoch !== this.refreshEpoch) {
                return;
            }
            globalStore.set(this.diffDataAtom, {
                original: response.original,
                modified: response.modified,
                fileName: path,
            });
            globalStore.set(this.loadingAtom, false);
        } catch (e) {
            if (epoch !== this.refreshEpoch) {
                return;
            }
            globalStore.set(this.errorAtom, formatGitRpcError(e, conn));
            globalStore.set(this.loadingAtom, false);
        }
    }
}
