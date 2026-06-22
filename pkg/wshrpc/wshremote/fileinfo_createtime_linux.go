// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshremote

import (
	"io/fs"

	"golang.org/x/sys/unix"
)

func fileCreateTimeMillis(path string, _ fs.FileInfo) int64 {
	var stat unix.Statx_t
	err := unix.Statx(unix.AT_FDCWD, path, unix.AT_SYMLINK_NOFOLLOW, unix.STATX_BTIME, &stat)
	if err != nil || stat.Mask&unix.STATX_BTIME == 0 || stat.Btime.Sec <= 0 {
		return 0
	}
	return int64(stat.Btime.Sec)*1000 + int64(stat.Btime.Nsec)/1e6
}
