// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshremote

import (
	"os"
	"path/filepath"
	"testing"
)

func TestStatToFileInfoMarksDirectorySymlinks(t *testing.T) {
	tempDir := t.TempDir()
	targetDir := filepath.Join(tempDir, "target")
	linkPath := filepath.Join(tempDir, "linked")

	if err := os.Mkdir(targetDir, 0755); err != nil {
		t.Fatalf("creating target dir: %v", err)
	}
	if err := os.Symlink(targetDir, linkPath); err != nil {
		t.Fatalf("creating symlink: %v", err)
	}

	linkInfo, err := os.Lstat(linkPath)
	if err != nil {
		t.Fatalf("lstat symlink: %v", err)
	}

	fileInfo := statToFileInfo(linkPath, linkInfo, false)

	if !fileInfo.Symlink {
		t.Fatalf("expected symlink metadata to be true")
	}
	if !fileInfo.IsDir {
		t.Fatalf("expected directory symlink to be expandable as a directory")
	}
	if fileInfo.MimeType != "directory" {
		t.Fatalf("expected directory symlink mimetype to be directory, got %q", fileInfo.MimeType)
	}
}
