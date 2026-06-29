// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { DiffViewer } from "@/app/view/codeeditor/diffviewer";
import { cn } from "@/util/util";
import { useAtomValue } from "jotai";
import React, { memo } from "react";
import type { GitDiffViewModel } from "./gitdiff-model";
import "./gitdiff.scss";

export const GitDiffView: React.FC<ViewComponentProps<GitDiffViewModel>> = memo(({ blockId, contentRef, model }) => {
    const diffData = useAtomValue(model.diffDataAtom);
    const loading = useAtomValue(model.loadingAtom);
    const error = useAtomValue(model.errorAtom);

    if (loading) {
        return (
            <div ref={contentRef} className="git-diff-view git-diff-centered">
                Loading diff...
            </div>
        );
    }

    if (error) {
        return (
            <div ref={contentRef} className={cn("git-diff-view", "git-diff-error")}>
                {error}
            </div>
        );
    }

    if (!diffData) {
        return (
            <div ref={contentRef} className="git-diff-view git-diff-centered">
                No diff data
            </div>
        );
    }

    return (
        <div ref={contentRef} className="git-diff-view">
            <DiffViewer
                blockId={blockId}
                original={diffData.original}
                modified={diffData.modified}
                fileName={diffData.fileName}
            />
        </div>
    );
});

GitDiffView.displayName = "GitDiffView";
