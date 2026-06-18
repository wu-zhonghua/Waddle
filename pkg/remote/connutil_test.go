// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package remote

import (
	"bytes"
	"fmt"
	"io"
	"testing"

	"github.com/waddledev/waddle/pkg/wps"
)

func TestWshInstallProgressWriterCopiesWithoutTerminalProgressLogs(t *testing.T) {
	var dst bytes.Buffer
	var logs []string
	writer := makeWshInstallProgressWriter(&dst, 100, "root@ace:5561", func(format string, args ...any) {
		logs = append(logs, fmt.Sprintf(format, args...))
	}, nil)

	_, err := io.Copy(writer, bytes.NewReader(bytes.Repeat([]byte("a"), 50)))
	if err != nil {
		t.Fatalf("copy should succeed: %v", err)
	}
	writer.Finish()

	if dst.Len() != 50 {
		t.Fatalf("expected copied bytes to reach destination, got %d", dst.Len())
	}
	if len(logs) != 0 {
		t.Fatalf("expected no terminal progress logs, got %#v", logs)
	}
}

func TestWshInstallProgressWriterPublishesEveryPercent(t *testing.T) {
	var dst bytes.Buffer
	var events []wps.WshInstallProgressData
	writer := makeWshInstallProgressWriter(&dst, 100, "root@ace:5561", nil, func(data wps.WshInstallProgressData) {
		events = append(events, data)
	})

	writer.Start()
	for range 3 {
		if _, err := writer.Write(bytes.Repeat([]byte("a"), 1)); err != nil {
			t.Fatalf("write should succeed: %v", err)
		}
	}
	if len(events) != 4 {
		t.Fatalf("expected start plus three 1%% events, got %#v", events)
	}
	for idx, percent := range []int{0, 1, 2, 3} {
		if events[idx].Percent != percent {
			t.Fatalf("expected event %d to be %d%%, got %#v", idx, percent, events[idx])
		}
	}
}

func TestWshInstallProgressWriterPublishesProgressEvents(t *testing.T) {
	var dst bytes.Buffer
	var events []wps.WshInstallProgressData
	writer := makeWshInstallProgressWriter(
		&dst,
		100,
		"root@ace:5561",
		nil,
		func(data wps.WshInstallProgressData) {
			events = append(events, data)
		},
	)

	writer.Start()
	_, err := writer.Write(bytes.Repeat([]byte("a"), 25))
	if err != nil {
		t.Fatalf("write should succeed: %v", err)
	}
	writer.Finish()

	if len(events) != 3 {
		t.Fatalf("expected start, running, and done events, got %#v", events)
	}
	if events[0].Status != "running" || events[0].Percent != 0 || events[0].ConnName != "root@ace:5561" {
		t.Fatalf("unexpected start event: %#v", events[0])
	}
	if events[1].Status != "running" || events[1].Percent != 25 || events[1].Written != 25 || events[1].Total != 100 {
		t.Fatalf("unexpected progress event: %#v", events[1])
	}
	if events[2].Status != "done" || events[2].Percent != 100 || events[2].Written != 100 {
		t.Fatalf("unexpected done event: %#v", events[2])
	}
}
