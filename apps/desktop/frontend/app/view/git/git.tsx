// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { cn, fireAndForget } from "@/util/util";
import { useAtom, useAtomValue } from "jotai";
import React, { memo } from "react";
import type { GitViewModel } from "./git-model";
import { isMissingGitRpcCommand } from "./git-model";
import type { GitFileBucket, GitFileGroups } from "./git-model";
import "./git.scss";

type GitGroupProps = {
    title: string;
    bucket: GitFileBucket;
    files: GitFileStatus[];
    model: GitViewModel;
    selectedPath: string;
    selectedStaged: boolean;
};

function getStatusLabel(file: GitFileStatus): string {
    switch (file.status) {
        case "added":
            return "A";
        case "deleted":
            return "D";
        case "renamed":
            return "R";
        case "copied":
            return "C";
        case "untracked":
            return "U";
        case "conflicted":
            return "!";
        case "modified":
        default:
            return "M";
    }
}

function getPathLabel(file: GitFileStatus): string {
    if (file.origpath != null && file.origpath !== "") {
        return `${file.origpath} -> ${file.path}`;
    }
    return file.path;
}

function GitFileRow({ file, bucket, model, selectedPath, selectedStaged }: Omit<GitGroupProps, "title" | "files"> & { file: GitFileStatus }) {
    const staged = bucket === "staged";
    const selected = selectedPath === file.path && selectedStaged === staged;
    const onStage = (event: React.MouseEvent) => {
        event.stopPropagation();
        fireAndForget(() => model.stage([file.path]));
    };
    const onUnstage = (event: React.MouseEvent) => {
        event.stopPropagation();
        fireAndForget(() => model.unstage([file.path]));
    };

    return (
        <div
            className={cn("git-file-row", selected && "git-file-row-selected")}
            title={getPathLabel(file)}
            onClick={() => fireAndForget(() => model.selectFile(file, staged))}
        >
            <span className={cn("git-status-badge", `git-status-${file.status}`)}>{getStatusLabel(file)}</span>
            <span className="git-file-name">{getPathLabel(file)}</span>
            {bucket === "staged" ? (
                <button className="git-icon-button" title="Unstage" onClick={onUnstage}>
                    <i className="fa fa-solid fa-minus"></i>
                </button>
            ) : (
                <button className="git-icon-button" title="Stage" onClick={onStage}>
                    <i className="fa fa-solid fa-plus"></i>
                </button>
            )}
        </div>
    );
}

function GitGroup({ title, bucket, files, model, selectedPath, selectedStaged }: GitGroupProps) {
    if (files.length === 0) {
        return null;
    }
    return (
        <section className="git-group">
            <div className="git-group-header">
                <span>{title}</span>
                <span className="git-group-count">{files.length}</span>
            </div>
            <div className="git-group-files">
                {files.map((file) => (
                    <GitFileRow
                        key={`${bucket}:${file.path}:${file.origpath ?? ""}`}
                        bucket={bucket}
                        file={file}
                        model={model}
                        selectedPath={selectedPath}
                        selectedStaged={selectedStaged}
                    />
                ))}
            </div>
        </section>
    );
}

function GitGroups({
    groups,
    model,
    selectedPath,
    selectedStaged,
}: {
    groups: GitFileGroups;
    model: GitViewModel;
    selectedPath: string;
    selectedStaged: boolean;
}) {
    return (
        <>
            <GitGroup
                title="Staged Changes"
                bucket="staged"
                files={groups.staged}
                model={model}
                selectedPath={selectedPath}
                selectedStaged={selectedStaged}
            />
            <GitGroup
                title="Changes"
                bucket="changes"
                files={groups.changes}
                model={model}
                selectedPath={selectedPath}
                selectedStaged={selectedStaged}
            />
            <GitGroup
                title="Untracked"
                bucket="untracked"
                files={groups.untracked}
                model={model}
                selectedPath={selectedPath}
                selectedStaged={selectedStaged}
            />
        </>
    );
}

export const GitView: React.FC<ViewComponentProps<GitViewModel>> = memo(({ contentRef, model }) => {
    const status = useAtomValue(model.statusAtom);
    const loading = useAtomValue(model.loadingAtom);
    const error = useAtomValue(model.errorAtom);
    const conn = useAtomValue(model.connection);
    const groups = useAtomValue(model.groupedFilesAtom);
    const canCommit = useAtomValue(model.canCommitAtom);
    const selectedPath = useAtomValue(model.selectedPathAtom);
    const selectedStaged = useAtomValue(model.selectedStagedAtom);
    const actionStatus = useAtomValue(model.actionStatusAtom);
    const reviewStatus = useAtomValue(model.reviewStatusAtom);
    const reviewLoading = useAtomValue(model.reviewLoadingAtom);
    const [message, setMessage] = useAtom(model.commitMessageAtom);
    const [projectPath, setProjectPath] = useAtom(model.projectPathAtom);
    const hasChanges = status?.files?.length > 0;
    const canUpdateRemoteHelper = conn !== "local" && isMissingGitRpcCommand(error);

    return (
        <div ref={contentRef} className="git-panel">
            <div className="git-summary">
                <div className="git-branch-line">
                    <i className="fa fa-solid fa-code-branch"></i>
                    <span className="git-branch-name">{status?.branch || "Git"}</span>
                    {status?.upstream ? <span className="git-upstream">{status.upstream}</span> : null}
                </div>
                {(status?.ahead ?? 0) > 0 || (status?.behind ?? 0) > 0 ? (
                    <div className="git-sync-line">
                        {status.ahead > 0 ? <span>↑ {status.ahead}</span> : null}
                        {status.behind > 0 ? <span>↓ {status.behind}</span> : null}
                    </div>
                ) : null}
                <form
                    className="git-project-form"
                    onSubmit={(event) => {
                        event.preventDefault();
                        fireAndForget(() => model.setProjectCwd(projectPath));
                    }}
                >
                    <label className="git-project-label" htmlFor={`git-project-${model.blockId}`}>
                        Project
                    </label>
                    <input
                        id={`git-project-${model.blockId}`}
                        className="git-project-input"
                        value={projectPath}
                        spellCheck={false}
                        onChange={(event) => setProjectPath(event.target.value)}
                    />
                    <button className="git-icon-button" type="submit" title="Use Project Path">
                        <i className="fa fa-solid fa-arrow-right"></i>
                    </button>
                    <button
                        className="git-icon-button"
                        type="button"
                        title="Use Project Path and Refresh"
                        onClick={() => fireAndForget(() => model.setProjectCwd(projectPath))}
                    >
                        <i className={cn("fa fa-solid fa-refresh", loading && "fa-spin")}></i>
                    </button>
                </form>
            </div>

            <div className="git-review-box">
                <div className="git-section-header">
                    <i className="fa fa-solid fa-search"></i>
                    <span>Agent Review</span>
                </div>
                <button
                    className="git-review-button"
                    disabled={reviewLoading}
                    onClick={() => fireAndForget(() => model.findIssues())}
                >
                    <i className={cn("fa fa-solid", reviewLoading ? "fa-spinner fa-spin" : "fa-search")}></i>
                    <span>{reviewLoading ? "Reviewing..." : "Find Issues"}</span>
                </button>
                <div className="git-review-subtitle">Review changes against main.</div>
                {reviewStatus ? (
                    <div className={cn("git-action-status", reviewStatus.isError && "git-action-status-error")}>
                        {reviewStatus.message}
                    </div>
                ) : null}
            </div>

            <div className="git-commit-box">
                <textarea
                    ref={model.focusRef}
                    value={message}
                    className="git-message-input"
                    placeholder="Message"
                    rows={2}
                    onChange={(event) => setMessage(event.target.value)}
                />
                <button
                    className={cn("git-commit-button", canCommit && "git-commit-button-ready")}
                    disabled={!canCommit}
                    onClick={() => fireAndForget(() => model.commit())}
                >
                    <i className="fa fa-solid fa-check"></i>
                    <span>Commit</span>
                </button>
                {actionStatus ? (
                    <div className={cn("git-action-status", actionStatus.isError && "git-action-status-error")}>
                        {actionStatus.message}
                    </div>
                ) : null}
            </div>

            <div className="git-body">
                <div className="git-file-list">
                    {loading && !hasChanges ? <div className="git-empty">Loading...</div> : null}
                    {error ? (
                        <div className="git-error">
                            <div>{error}</div>
                            {canUpdateRemoteHelper ? (
                                <button
                                    className="git-secondary-button"
                                    onClick={() => fireAndForget(() => model.updateRemoteHelper())}
                                >
                                    Update + reconnect remote helper
                                </button>
                            ) : null}
                            {actionStatus ? (
                                <div className={cn("git-inline-status", actionStatus.isError && "git-action-status-error")}>
                                    {actionStatus.message}
                                </div>
                            ) : null}
                        </div>
                    ) : null}
                    {!loading && !error && !hasChanges ? <div className="git-empty">No changes</div> : null}
                    {hasChanges ? (
                        <GitGroups groups={groups} model={model} selectedPath={selectedPath} selectedStaged={selectedStaged} />
                    ) : null}
                </div>
            </div>
        </div>
    );
});

GitView.displayName = "GitView";
