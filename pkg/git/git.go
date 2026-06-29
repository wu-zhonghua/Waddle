// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package git

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const GitFileStatusAdded = "added"
const GitFileStatusModified = "modified"
const GitFileStatusDeleted = "deleted"
const GitFileStatusRenamed = "renamed"
const GitFileStatusCopied = "copied"
const GitFileStatusChanged = "changed"
const GitFileStatusUntracked = "untracked"
const GitFileStatusConflicted = "conflicted"

const gitCommandTimeout = 30 * time.Second

type FileStatus struct {
	Path           string `json:"path"`
	OrigPath       string `json:"origpath,omitempty"`
	IndexStatus    string `json:"indexstatus,omitempty"`
	WorktreeStatus string `json:"worktreestatus,omitempty"`
	Status         string `json:"status"`
}

type StatusData struct {
	Cwd        string       `json:"cwd"`
	Root       string       `json:"root"`
	Branch     string       `json:"branch,omitempty"`
	Upstream   string       `json:"upstream,omitempty"`
	Ahead      int          `json:"ahead,omitempty"`
	Behind     int          `json:"behind,omitempty"`
	Files      []FileStatus `json:"files"`
	HasChanges bool         `json:"haschanges"`
}

type CommitResult struct {
	Hash   string `json:"hash"`
	Output string `json:"output"`
}

type FileDiffData struct {
	Original string `json:"original"`
	Modified string `json:"modified"`
}

type ReviewDiffData struct {
	Base string `json:"base"`
	Diff string `json:"diff"`
}

func normalizeCwd(cwd string) (string, error) {
	if strings.TrimSpace(cwd) == "" {
		return "", fmt.Errorf("cwd is required")
	}
	if cwd == "~" || strings.HasPrefix(cwd, "~/") {
		homeDir, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("cannot resolve home directory: %w", err)
		}
		cwd = filepath.Join(homeDir, strings.TrimPrefix(cwd, "~"))
	}
	return filepath.Clean(cwd), nil
}

func runGit(ctx context.Context, cwd string, args ...string) (string, error) {
	cwd, err := normalizeCwd(cwd)
	if err != nil {
		return "", err
	}
	runCtx, cancelFn := context.WithTimeout(ctx, gitCommandTimeout)
	defer cancelFn()
	cmdArgs := append([]string{"-C", cwd}, args...)
	cmd := exec.CommandContext(runCtx, "git", cmdArgs...)
	out, err := cmd.CombinedOutput()
	outStr := string(out)
	if runCtx.Err() != nil {
		return outStr, fmt.Errorf("git %s timed out: %w", strings.Join(args, " "), runCtx.Err())
	}
	if err != nil {
		return outStr, fmt.Errorf("git %s failed: %w: %s", strings.Join(args, " "), err, strings.TrimSpace(outStr))
	}
	return outStr, nil
}

func parseBranchAheadBehind(value string) (int, int) {
	var ahead int
	var behind int
	for _, part := range strings.Fields(value) {
		if strings.HasPrefix(part, "+") {
			ahead, _ = strconv.Atoi(strings.TrimPrefix(part, "+"))
		}
		if strings.HasPrefix(part, "-") {
			behind, _ = strconv.Atoi(strings.TrimPrefix(part, "-"))
		}
	}
	return ahead, behind
}

func fileStatusFromXY(indexStatus string, worktreeStatus string) string {
	if indexStatus == "U" || worktreeStatus == "U" || indexStatus == "A" && worktreeStatus == "A" || indexStatus == "D" && worktreeStatus == "D" {
		return GitFileStatusConflicted
	}
	status := indexStatus
	if status == "." {
		status = worktreeStatus
	}
	switch status {
	case "A":
		return GitFileStatusAdded
	case "M":
		return GitFileStatusModified
	case "D":
		return GitFileStatusDeleted
	case "R":
		return GitFileStatusRenamed
	case "C":
		return GitFileStatusCopied
	default:
		return GitFileStatusChanged
	}
}

func makeFileStatus(path string, origPath string, xy string) FileStatus {
	indexStatus := "."
	worktreeStatus := "."
	if len(xy) >= 2 {
		indexStatus = string(xy[0])
		worktreeStatus = string(xy[1])
	}
	return FileStatus{
		Path:           path,
		OrigPath:       origPath,
		IndexStatus:    indexStatus,
		WorktreeStatus: worktreeStatus,
		Status:         fileStatusFromXY(indexStatus, worktreeStatus),
	}
}

func ParseStatusPorcelainV2(output []byte) (*StatusData, error) {
	status := &StatusData{}
	records := strings.Split(string(output), "\x00")
	for idx := 0; idx < len(records); idx++ {
		record := records[idx]
		if record == "" {
			continue
		}
		if strings.HasPrefix(record, "# branch.head ") {
			status.Branch = strings.TrimPrefix(record, "# branch.head ")
			continue
		}
		if strings.HasPrefix(record, "# branch.upstream ") {
			status.Upstream = strings.TrimPrefix(record, "# branch.upstream ")
			continue
		}
		if strings.HasPrefix(record, "# branch.ab ") {
			status.Ahead, status.Behind = parseBranchAheadBehind(strings.TrimPrefix(record, "# branch.ab "))
			continue
		}
		if strings.HasPrefix(record, "1 ") {
			fields := strings.SplitN(record, " ", 9)
			if len(fields) != 9 {
				return nil, fmt.Errorf("invalid porcelain v2 file record %q", record)
			}
			status.Files = append(status.Files, makeFileStatus(fields[8], "", fields[1]))
			continue
		}
		if strings.HasPrefix(record, "2 ") {
			fields := strings.SplitN(record, " ", 10)
			if len(fields) != 10 {
				return nil, fmt.Errorf("invalid porcelain v2 rename record %q", record)
			}
			origPath := ""
			if idx+1 < len(records) {
				idx++
				origPath = records[idx]
			}
			status.Files = append(status.Files, makeFileStatus(fields[9], origPath, fields[1]))
			continue
		}
		if strings.HasPrefix(record, "? ") {
			status.Files = append(status.Files, FileStatus{
				Path:           strings.TrimPrefix(record, "? "),
				IndexStatus:    "?",
				WorktreeStatus: "?",
				Status:         GitFileStatusUntracked,
			})
			continue
		}
		if strings.HasPrefix(record, "u ") {
			fields := strings.Split(record, " ")
			if len(fields) < 2 {
				return nil, fmt.Errorf("invalid porcelain v2 unmerged record %q", record)
			}
			status.Files = append(status.Files, FileStatus{
				Path:           fields[len(fields)-1],
				IndexStatus:    "U",
				WorktreeStatus: "U",
				Status:         GitFileStatusConflicted,
			})
		}
	}
	status.HasChanges = len(status.Files) > 0
	return status, nil
}

func Status(ctx context.Context, cwd string) (*StatusData, error) {
	normalizedCwd, err := normalizeCwd(cwd)
	if err != nil {
		return nil, err
	}
	root, err := runGit(ctx, normalizedCwd, "rev-parse", "--show-toplevel")
	if err != nil {
		return nil, err
	}
	out, err := runGit(ctx, normalizedCwd, "status", "--porcelain=v2", "-b", "-z")
	if err != nil {
		return nil, err
	}
	status, err := ParseStatusPorcelainV2([]byte(out))
	if err != nil {
		return nil, err
	}
	status.Cwd = normalizedCwd
	status.Root = strings.TrimSpace(root)
	return status, nil
}

func Diff(ctx context.Context, cwd string, path string, staged bool) (string, error) {
	args := []string{"diff"}
	if staged {
		args = append(args, "--cached")
	}
	args = append(args, "--")
	if path != "" {
		args = append(args, path)
	}
	return runGit(ctx, cwd, args...)
}

func showGitBlob(ctx context.Context, cwd string, rev string, path string) (string, bool, error) {
	if strings.TrimSpace(path) == "" {
		return "", false, nil
	}
	spec := ":" + path
	if rev != "" {
		spec = rev + ":" + path
	}
	out, err := runGit(ctx, cwd, "show", spec)
	if err != nil {
		return "", false, nil
	}
	return out, true, nil
}

func readWorktreeFile(cwd string, path string) (string, bool, error) {
	if strings.TrimSpace(path) == "" {
		return "", false, nil
	}
	normalizedCwd, err := normalizeCwd(cwd)
	if err != nil {
		return "", false, err
	}
	cleanPath := filepath.Clean(path)
	if filepath.IsAbs(cleanPath) || cleanPath == ".." || strings.HasPrefix(cleanPath, ".."+string(filepath.Separator)) {
		return "", false, fmt.Errorf("invalid git path %q", path)
	}
	data, err := os.ReadFile(filepath.Join(normalizedCwd, cleanPath))
	if os.IsNotExist(err) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return string(data), true, nil
}

func FileDiff(ctx context.Context, cwd string, path string, origPath string, staged bool) (*FileDiffData, error) {
	if strings.TrimSpace(path) == "" {
		return nil, fmt.Errorf("path is required")
	}
	originalPath := path
	if strings.TrimSpace(origPath) != "" {
		originalPath = origPath
	}
	var original string
	var modified string
	var err error
	if staged {
		original, _, err = showGitBlob(ctx, cwd, "HEAD", originalPath)
		if err != nil {
			return nil, err
		}
		modified, _, err = showGitBlob(ctx, cwd, "", path)
		if err != nil {
			return nil, err
		}
		return &FileDiffData{Original: original, Modified: modified}, nil
	}
	original, _, err = showGitBlob(ctx, cwd, "", originalPath)
	if err != nil {
		return nil, err
	}
	modified, _, err = readWorktreeFile(cwd, path)
	if err != nil {
		return nil, err
	}
	return &FileDiffData{Original: original, Modified: modified}, nil
}

func resolveReviewBase(ctx context.Context, cwd string, base string) (string, error) {
	trimmedBase := strings.TrimSpace(base)
	candidates := make([]string, 0, 5)
	if trimmedBase != "" {
		candidates = append(candidates, trimmedBase)
	}
	if trimmedBase == "" || trimmedBase == "main" {
		candidates = append(candidates, "main", "origin/main", "master", "origin/master")
	}
	seen := make(map[string]bool)
	tried := make([]string, 0, len(candidates))
	for _, candidate := range candidates {
		if seen[candidate] {
			continue
		}
		seen[candidate] = true
		tried = append(tried, candidate)
		if _, err := runGit(ctx, cwd, "merge-base", candidate, "HEAD"); err == nil {
			return candidate, nil
		}
	}
	return "", fmt.Errorf("cannot find review base; tried %s", strings.Join(tried, ", "))
}

func ReviewDiff(ctx context.Context, cwd string, base string) (*ReviewDiffData, error) {
	baseRef, err := resolveReviewBase(ctx, cwd, base)
	if err != nil {
		return nil, err
	}
	branchDiff, err := runGit(ctx, cwd, "diff", "--find-renames", baseRef+"...HEAD")
	if err != nil {
		return nil, err
	}
	workingDiff, err := runGit(ctx, cwd, "diff", "--find-renames", "HEAD")
	if err != nil {
		return nil, err
	}
	untrackedDiff, err := UntrackedDiff(ctx, cwd)
	if err != nil {
		return nil, err
	}
	diff := joinReviewDiffSections(
		ReviewDiffSection{Title: "Committed changes against " + baseRef, Diff: branchDiff},
		ReviewDiffSection{Title: "Uncommitted tracked changes against HEAD", Diff: workingDiff},
		ReviewDiffSection{Title: "Untracked files", Diff: untrackedDiff},
	)
	return &ReviewDiffData{Base: baseRef, Diff: diff}, nil
}

type ReviewDiffSection struct {
	Title string
	Diff  string
}

func joinReviewDiffSections(sections ...ReviewDiffSection) string {
	var builder strings.Builder
	for _, section := range sections {
		if strings.TrimSpace(section.Diff) == "" {
			continue
		}
		if builder.Len() > 0 {
			builder.WriteString("\n")
		}
		if section.Title != "" {
			builder.WriteString("# ")
			builder.WriteString(section.Title)
			builder.WriteString("\n")
		}
		builder.WriteString(section.Diff)
		if !strings.HasSuffix(section.Diff, "\n") {
			builder.WriteString("\n")
		}
	}
	return builder.String()
}

func UntrackedDiff(ctx context.Context, cwd string) (string, error) {
	out, err := runGit(ctx, cwd, "ls-files", "--others", "--exclude-standard", "-z")
	if err != nil {
		return "", err
	}
	var builder strings.Builder
	for _, path := range strings.Split(out, "\x00") {
		if path == "" {
			continue
		}
		contents, ok, err := readWorktreeFile(cwd, path)
		if err != nil {
			return "", err
		}
		if !ok {
			continue
		}
		if builder.Len() > 0 {
			builder.WriteString("\n")
		}
		builder.WriteString(makeAddedFilePatch(path, contents))
	}
	return builder.String(), nil
}

func makeAddedFilePatch(path string, contents string) string {
	lineCount := strings.Count(contents, "\n")
	if contents != "" && !strings.HasSuffix(contents, "\n") {
		lineCount++
	}
	var builder strings.Builder
	builder.WriteString("diff --git a/")
	builder.WriteString(path)
	builder.WriteString(" b/")
	builder.WriteString(path)
	builder.WriteString("\nnew file mode 100644\nindex 0000000..0000000\n--- /dev/null\n+++ b/")
	builder.WriteString(path)
	builder.WriteString("\n")
	if lineCount == 0 {
		return builder.String()
	}
	builder.WriteString(fmt.Sprintf("@@ -0,0 +1,%d @@\n", lineCount))
	for _, line := range strings.SplitAfter(contents, "\n") {
		if line == "" {
			continue
		}
		builder.WriteString("+")
		builder.WriteString(line)
		if !strings.HasSuffix(line, "\n") {
			builder.WriteString("\n")
		}
	}
	return builder.String()
}

func validatePaths(paths []string) error {
	if len(paths) == 0 {
		return fmt.Errorf("at least one path is required")
	}
	for _, path := range paths {
		if strings.TrimSpace(path) == "" {
			return fmt.Errorf("empty paths are not allowed")
		}
	}
	return nil
}

func Stage(ctx context.Context, cwd string, paths []string) error {
	if err := validatePaths(paths); err != nil {
		return err
	}
	args := append([]string{"add", "--"}, paths...)
	_, err := runGit(ctx, cwd, args...)
	return err
}

func Unstage(ctx context.Context, cwd string, paths []string) error {
	if err := validatePaths(paths); err != nil {
		return err
	}
	args := append([]string{"restore", "--staged", "--"}, paths...)
	_, err := runGit(ctx, cwd, args...)
	return err
}

func Commit(ctx context.Context, cwd string, message string) (*CommitResult, error) {
	message = strings.TrimSpace(message)
	if message == "" {
		return nil, fmt.Errorf("commit message is required")
	}
	out, err := runGit(ctx, cwd, "commit", "-m", message)
	if err != nil {
		return nil, err
	}
	hash, err := runGit(ctx, cwd, "rev-parse", "HEAD")
	if err != nil {
		return nil, err
	}
	return &CommitResult{
		Hash:   strings.TrimSpace(hash),
		Output: out,
	}, nil
}
