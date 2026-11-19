// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

//go:build !darwin && !linux

package wshremote

import "io/fs"

func fileCreateTimeMillis(_ string, _ fs.FileInfo) int64 {
	return 0
}
