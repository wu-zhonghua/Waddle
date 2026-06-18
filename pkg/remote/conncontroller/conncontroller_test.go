// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package conncontroller

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestWshInstallContextIgnoresParentDeadline(t *testing.T) {
	parentCtx, cancelFn := context.WithTimeout(context.Background(), time.Nanosecond)
	defer cancelFn()
	<-parentCtx.Done()

	installCtx := wshInstallContext(parentCtx)

	if deadline, ok := installCtx.Deadline(); ok {
		t.Fatalf("wsh install context should not inherit parent deadline, got %v", deadline)
	}
	select {
	case <-installCtx.Done():
		t.Fatal("wsh install context should not be canceled by parent deadline")
	default:
	}
	if err := installCtx.Err(); err != nil {
		t.Fatalf("wsh install context should ignore parent deadline error, got %v", err)
	}
}

func TestIsWshVersionUpToDateTreatsSegfaultAsNeedsInstall(t *testing.T) {
	isUpToDate, clientVersion, osArchStr, err := IsWshVersionUpToDate(
		context.Background(),
		"bash: line 1: 15929 Segmentation fault      ~/.waddle/bin/wsh version 2>&1",
	)

	if err != nil {
		t.Fatalf("segfaulting wsh should not stop reinstall detection: %v", err)
	}
	if isUpToDate {
		t.Fatal("segfaulting wsh should require reinstall")
	}
	if clientVersion != "not-installed" {
		t.Fatalf("segfaulting wsh should be treated as not-installed, got %q", clientVersion)
	}
	if osArchStr != "" {
		t.Fatalf("segfaulting wsh should force platform reprobe, got %q", osArchStr)
	}
}

func TestGetClientPlatformForInstallFallsBackForNoisyOsArchStr(t *testing.T) {
	fallbackCalled := false
	clientOs, clientArch, err := getClientPlatformForInstall(
		context.Background(),
		"/bin/bash: line 1: 15606 Segmentation fault      ~/.waddle/bin/wsh version 2> /dev/null",
		func() (string, string, error) {
			fallbackCalled = true
			return "linux", "x64", nil
		},
	)

	if err != nil {
		t.Fatalf("fallback platform probe should succeed: %v", err)
	}
	if !fallbackCalled {
		t.Fatal("invalid platform string should trigger fallback platform probe")
	}
	if clientOs != "linux" || clientArch != "x64" {
		t.Fatalf("unexpected fallback platform: %s %s", clientOs, clientArch)
	}
}

func TestGetClientPlatformForInstallUsesValidOsArchStr(t *testing.T) {
	fallbackErr := errors.New("fallback should not be called")
	clientOs, clientArch, err := getClientPlatformForInstall(
		context.Background(),
		"Linux x86_64",
		func() (string, string, error) {
			return "", "", fallbackErr
		},
	)

	if err != nil {
		t.Fatalf("valid platform string should parse without fallback: %v", err)
	}
	if clientOs != "linux" || clientArch != "x64" {
		t.Fatalf("unexpected parsed platform: %s %s", clientOs, clientArch)
	}
}
