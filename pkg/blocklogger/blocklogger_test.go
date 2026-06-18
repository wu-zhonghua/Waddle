// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package blocklogger

import (
	"context"
	"encoding/base64"
	"testing"

	"github.com/waddledev/waddle/pkg/wshrpc"
)

func readQueuedLog(t *testing.T) string {
	t.Helper()
	select {
	case data := <-outputChan:
		decoded, err := base64.StdEncoding.DecodeString(data.Data64)
		if err != nil {
			t.Fatalf("expected base64 log data to decode: %v", err)
		}
		return string(decoded)
	default:
		t.Fatal("expected queued log output")
	}
	return ""
}

func expectNoQueuedLog(t *testing.T) {
	t.Helper()
	select {
	case data := <-outputChan:
		t.Fatalf("expected no queued log output, got %#v", data)
	default:
	}
}

func TestOutputfWritesWhenInfoLogsAreDisabled(t *testing.T) {
	outputChan = make(chan wshrpc.CommandControllerAppendOutputData, outputBufferSize)
	ctx := ContextWithLogBlockIdWithInfo(context.Background(), "block-1", false, false)

	Infof(ctx, "hidden")
	expectNoQueuedLog(t)

	Outputf(ctx, "visible %d\n", 1)

	if got := readQueuedLog(t); got != "visible 1\r\n" {
		t.Fatalf("expected visible output, got %q", got)
	}
}

func TestInfofStillRequiresInfoLogsEnabled(t *testing.T) {
	outputChan = make(chan wshrpc.CommandControllerAppendOutputData, outputBufferSize)
	ctx := ContextWithLogBlockIdWithInfo(context.Background(), "block-1", true, false)

	Infof(ctx, "visible")

	if got := readQueuedLog(t); got != "visible" {
		t.Fatalf("expected info output, got %q", got)
	}
}
