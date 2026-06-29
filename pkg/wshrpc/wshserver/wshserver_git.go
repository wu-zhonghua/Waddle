// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshserver

import (
	"context"

	gitops "github.com/waddledev/waddle/pkg/git"
	"github.com/waddledev/waddle/pkg/wshrpc"
)

func gitStatusToRpc(status *gitops.StatusData) *wshrpc.GitStatusData {
	files := make([]wshrpc.GitFileStatus, 0, len(status.Files))
	for _, file := range status.Files {
		files = append(files, wshrpc.GitFileStatus{
			Path:           file.Path,
			OrigPath:       file.OrigPath,
			IndexStatus:    file.IndexStatus,
			WorktreeStatus: file.WorktreeStatus,
			Status:         file.Status,
		})
	}
	return &wshrpc.GitStatusData{
		Cwd:        status.Cwd,
		Root:       status.Root,
		Branch:     status.Branch,
		Upstream:   status.Upstream,
		Ahead:      status.Ahead,
		Behind:     status.Behind,
		Files:      files,
		HasChanges: status.HasChanges,
	}
}

func (ws *WshServer) GitStatusCommand(ctx context.Context, data wshrpc.CommandGitStatusData) (*wshrpc.GitStatusData, error) {
	status, err := gitops.Status(ctx, data.Cwd)
	if err != nil {
		return nil, err
	}
	return gitStatusToRpc(status), nil
}

func (ws *WshServer) GitDiffCommand(ctx context.Context, data wshrpc.CommandGitDiffData) (*wshrpc.CommandGitDiffRtnData, error) {
	diff, err := gitops.Diff(ctx, data.Cwd, data.Path, data.Staged)
	if err != nil {
		return nil, err
	}
	return &wshrpc.CommandGitDiffRtnData{Diff: diff}, nil
}

func (ws *WshServer) GitFileDiffCommand(ctx context.Context, data wshrpc.CommandGitFileDiffData) (*wshrpc.CommandGitFileDiffRtnData, error) {
	diff, err := gitops.FileDiff(ctx, data.Cwd, data.Path, data.OrigPath, data.Staged)
	if err != nil {
		return nil, err
	}
	return &wshrpc.CommandGitFileDiffRtnData{
		Original: diff.Original,
		Modified: diff.Modified,
	}, nil
}

func (ws *WshServer) GitReviewDiffCommand(ctx context.Context, data wshrpc.CommandGitReviewDiffData) (*wshrpc.CommandGitReviewDiffRtnData, error) {
	diff, err := gitops.ReviewDiff(ctx, data.Cwd, data.Base)
	if err != nil {
		return nil, err
	}
	return &wshrpc.CommandGitReviewDiffRtnData{
		Base: diff.Base,
		Diff: diff.Diff,
	}, nil
}

func (ws *WshServer) GitStageCommand(ctx context.Context, data wshrpc.CommandGitStageData) error {
	return gitops.Stage(ctx, data.Cwd, data.Paths)
}

func (ws *WshServer) GitUnstageCommand(ctx context.Context, data wshrpc.CommandGitStageData) error {
	return gitops.Unstage(ctx, data.Cwd, data.Paths)
}

func (ws *WshServer) GitCommitCommand(ctx context.Context, data wshrpc.CommandGitCommitData) (*wshrpc.CommandGitCommitRtnData, error) {
	result, err := gitops.Commit(ctx, data.Cwd, data.Message)
	if err != nil {
		return nil, err
	}
	return &wshrpc.CommandGitCommitRtnData{
		Hash:   result.Hash,
		Output: result.Output,
	}, nil
}
