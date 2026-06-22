// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshremote

import (
	"io/fs"
	"syscall"
)

func fileCreateTimeMillis(_ string, finfo fs.FileInfo) int64 {
	stat, ok := finfo.Sys().(*syscall.Stat_t)
	if !ok {
		return 0
	}
	return stat.Birthtimespec.Sec*1000 + int64(stat.Birthtimespec.Nsec)/1e6
}
