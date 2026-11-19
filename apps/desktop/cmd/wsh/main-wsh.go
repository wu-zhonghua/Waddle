// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"github.com/waddledev/waddle/cmd/wsh/cmd"
	"github.com/waddledev/waddle/pkg/wavebase"
)

// set by main-server.go
var WaddleVersion = "0.0.0"
var BuildTime = "0"

func main() {
	wavebase.WaddleVersion = WaddleVersion
	wavebase.BuildTime = BuildTime
	cmd.Execute()
}
