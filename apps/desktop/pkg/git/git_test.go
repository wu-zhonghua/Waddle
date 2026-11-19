// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package git

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func requireGit(t *testing.T) {
	t.Helper()
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git binary not available")
	}
}

func runGitTest(t *testing.T, dir string, args ...string) {
	t.Helper()
	cmd := exec.Command("git", append([]string{"-C", dir}, args...)...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("git %v failed: %v\n%s", args, err, string(out))
	}
}

func writeFile(t *testing.T, dir string, name string, contents string) {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		t.Fatalf("making parent dir for %s: %v", name, err)
	}
	if err := os.WriteFile(path, []byte(contents), 0644); err != nil {
		t.Fatalf("writing %s: %v", name, err)
	}
}

func TestParseStatusPorcelainV2GroupsBranchAndFileStates(t *testing.T) {
	output := "# branch.oid abc123\x00" +
		"# branch.head main\x00" +
		"# branch.upstream origin/main\x00" +
		"# branch.ab +2 -1\x00" +
		"1 M. N... 100644 100644 100644 old old file-a.txt\x00" +
		"1 .D N... 100644 100644 000000 old old gone.txt\x00" +
		"? new.txt\x00" +
		"2 R. N... 100644 100644 100644 old old R100 renamed.txt\x00old-name.txt\x00"

	status, err := ParseStatusPorcelainV2([]byte(output))
	if err != nil {
		t.Fatalf("ParseStatusPorcelainV2 returned error: %v", err)
	}

	if status.Branch != "main" {
		t.Fatalf("expected branch main, got %q", status.Branch)
	}
	if status.Upstream != "origin/main" {
		t.Fatalf("expected upstream origin/main, got %q", status.Upstream)
	}
	if status.Ahead != 2 || status.Behind != 1 {
		t.Fatalf("expected ahead/behind 2/1, got %d/%d", status.Ahead, status.Behind)
	}
	if len(status.Files) != 4 {
		t.Fatalf("expected 4 files, got %d", len(status.Files))
	}
	if status.Files[0].Path != "file-a.txt" || status.Files[0].IndexStatus != "M" || status.Files[0].WorktreeStatus != "." {
		t.Fatalf("unexpected first file: %#v", status.Files[0])
	}
	if status.Files[1].Path != "gone.txt" || status.Files[1].WorktreeStatus != "D" {
		t.Fatalf("unexpected deleted file: %#v", status.Files[1])
	}
	if status.Files[2].Path != "new.txt" || status.Files[2].Status != GitFileStatusUntracked {
		t.Fatalf("unexpected untracked file: %#v", status.Files[2])
	}
	if status.Files[3].Path != "renamed.txt" || status.Files[3].OrigPath != "old-name.txt" || status.Files[3].IndexStatus != "R" {
		t.Fatalf("unexpected renamed file: %#v", status.Files[3])
	}
}

func TestGitWorkflowStatusStageUnstageDiffAndCommit(t *testing.T) {
	requireGit(t)

	ctx := context.Background()
	dir := t.TempDir()
	runGitTest(t, dir, "init", "-b", "main")
	runGitTest(t, dir, "config", "user.email", "waddle@example.com")
	runGitTest(t, dir, "config", "user.name", "Waddle Test")
	writeFile(t, dir, "tracked.txt", "before\n")
	runGitTest(t, dir, "add", "tracked.txt")
	runGitTest(t, dir, "commit", "-m", "initial")

	writeFile(t, dir, "tracked.txt", "after\n")
	writeFile(t, dir, "new.txt", "hello\n")

	status, err := Status(ctx, dir)
	if err != nil {
		t.Fatalf("Status returned error: %v", err)
	}
	expectedRoot, err := filepath.EvalSymlinks(dir)
	if err != nil {
		t.Fatalf("resolving temp dir symlink: %v", err)
	}
	if status.Root != expectedRoot {
		t.Fatalf("expected root %q, got %q", expectedRoot, status.Root)
	}
	if len(status.Files) != 2 {
		t.Fatalf("expected 2 changed files, got %d: %#v", len(status.Files), status.Files)
	}

	diff, err := Diff(ctx, dir, "tracked.txt", false)
	if err != nil {
		t.Fatalf("Diff returned error: %v", err)
	}
	if diff == "" {
		t.Fatalf("expected non-empty diff")
	}

	if err := Stage(ctx, dir, []string{"tracked.txt"}); err != nil {
		t.Fatalf("Stage returned error: %v", err)
	}
	status, err = Status(ctx, dir)
	if err != nil {
		t.Fatalf("Status after stage returned error: %v", err)
	}
	if status.Files[0].Path != "tracked.txt" || status.Files[0].IndexStatus != "M" {
		t.Fatalf("expected tracked.txt staged, got %#v", status.Files)
	}

	if err := Unstage(ctx, dir, []string{"tracked.txt"}); err != nil {
		t.Fatalf("Unstage returned error: %v", err)
	}
	status, err = Status(ctx, dir)
	if err != nil {
		t.Fatalf("Status after unstage returned error: %v", err)
	}
	if status.Files[0].Path != "tracked.txt" || status.Files[0].IndexStatus != "." || status.Files[0].WorktreeStatus != "M" {
		t.Fatalf("expected tracked.txt unstaged, got %#v", status.Files)
	}

	if err := Stage(ctx, dir, []string{"tracked.txt", "new.txt"}); err != nil {
		t.Fatalf("Stage all returned error: %v", err)
	}
	commit, err := Commit(ctx, dir, "update files")
	if err != nil {
		t.Fatalf("Commit returned error: %v", err)
	}
	if commit.Hash == "" {
		t.Fatalf("expected commit hash")
	}
	status, err = Status(ctx, dir)
	if err != nil {
		t.Fatalf("Status after commit returned error: %v", err)
	}
	if len(status.Files) != 0 {
		t.Fatalf("expected clean repo after commit, got %#v", status.Files)
	}
}

func TestFileDiffReturnsWorkingTreeAndStagedSides(t *testing.T) {
	requireGit(t)

	ctx := context.Background()
	dir := t.TempDir()
	runGitTest(t, dir, "init", "-b", "main")
	runGitTest(t, dir, "config", "user.email", "waddle@example.com")
	runGitTest(t, dir, "config", "user.name", "Waddle Test")
	writeFile(t, dir, "tracked.txt", "before\n")
	runGitTest(t, dir, "add", "tracked.txt")
	runGitTest(t, dir, "commit", "-m", "initial")

	writeFile(t, dir, "tracked.txt", "after\n")
	diff, err := FileDiff(ctx, dir, "tracked.txt", "", false)
	if err != nil {
		t.Fatalf("FileDiff unstaged returned error: %v", err)
	}
	if diff.Original != "before\n" || diff.Modified != "after\n" {
		t.Fatalf("unexpected unstaged file diff: %#v", diff)
	}

	if err := Stage(ctx, dir, []string{"tracked.txt"}); err != nil {
		t.Fatalf("Stage returned error: %v", err)
	}
	writeFile(t, dir, "tracked.txt", "after plus worktree\n")
	diff, err = FileDiff(ctx, dir, "tracked.txt", "", true)
	if err != nil {
		t.Fatalf("FileDiff staged returned error: %v", err)
	}
	if diff.Original != "before\n" || diff.Modified != "after\n" {
		t.Fatalf("unexpected staged file diff: %#v", diff)
	}
}

func TestReviewDiffUsesMainAsBase(t *testing.T) {
	requireGit(t)

	ctx := context.Background()
	dir := t.TempDir()
	runGitTest(t, dir, "init", "-b", "main")
	runGitTest(t, dir, "config", "user.email", "waddle@example.com")
	runGitTest(t, dir, "config", "user.name", "Waddle Test")
	writeFile(t, dir, "tracked.txt", "before\n")
	runGitTest(t, dir, "add", "tracked.txt")
	runGitTest(t, dir, "commit", "-m", "initial")
	runGitTest(t, dir, "checkout", "-b", "feature")
	writeFile(t, dir, "tracked.txt", "after\n")
	runGitTest(t, dir, "add", "tracked.txt")
	runGitTest(t, dir, "commit", "-m", "feature update")
	writeFile(t, dir, "tracked.txt", "after plus worktree\n")
	writeFile(t, dir, "new-untracked.txt", "untracked\n")

	reviewDiff, err := ReviewDiff(ctx, dir, "main")
	if err != nil {
		t.Fatalf("ReviewDiff returned error: %v", err)
	}
	if reviewDiff.Base != "main" {
		t.Fatalf("expected base main, got %q", reviewDiff.Base)
	}
	if !strings.Contains(reviewDiff.Diff, "+after") {
		t.Fatalf("expected review diff to include new content, got:\n%s", reviewDiff.Diff)
	}
	if !strings.Contains(reviewDiff.Diff, "+after plus worktree") {
		t.Fatalf("expected review diff to include working tree content, got:\n%s", reviewDiff.Diff)
	}
	if !strings.Contains(reviewDiff.Diff, "+untracked") {
		t.Fatalf("expected review diff to include untracked content, got:\n%s", reviewDiff.Diff)
	}
}
