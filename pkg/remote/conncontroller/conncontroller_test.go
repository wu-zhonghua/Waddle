// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package conncontroller

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/waddledev/waddle/pkg/wavebase"
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

func TestIsWshVersionUpToDateRequiresExactLocalVersion(t *testing.T) {
	origVersion := wavebase.WaddleVersion
	wavebase.WaddleVersion = "1.2.3"
	t.Cleanup(func() {
		wavebase.WaddleVersion = origVersion
	})

	tests := []struct {
		name     string
		line     string
		expected bool
	}{
		{
			name:     "matching remote version is up-to-date",
			line:     "wsh v1.2.3",
			expected: true,
		},
		{
			name:     "older remote version needs update",
			line:     "wsh v1.2.2",
			expected: false,
		},
		{
			name:     "newer remote version still needs local version sync",
			line:     "wsh v1.2.4",
			expected: false,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			isUpToDate, clientVersion, osArchStr, err := IsWshVersionUpToDate(context.Background(), test.line)
			if err != nil {
				t.Fatalf("checking wsh version should not error: %v", err)
			}
			if isUpToDate != test.expected {
				t.Fatalf("expected up-to-date=%v for %q, got %v", test.expected, test.line, isUpToDate)
			}
			if clientVersion != strings.Fields(test.line)[1] {
				t.Fatalf("expected client version from line, got %q", clientVersion)
			}
			if osArchStr != "" {
				t.Fatalf("versioned wsh output should not include os/arch, got %q", osArchStr)
			}
		})
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

func TestShouldRetryWshFailureForConnserverStartErrors(t *testing.T) {
	for _, code := range []string{NoWshCode_ConnServerStartError, NoWshCode_PostInstallStartError} {
		if !shouldRetryWshFailure(WshCheckResult{NoWshCode: code}) {
			t.Fatalf("expected %s to retry", code)
		}
	}
}

func TestShouldNotRetryWshFailureForUserOrConfigDecisions(t *testing.T) {
	for _, code := range []string{NoWshCode_Disabled, NoWshCode_UserDeclined, NoWshCode_PermissionError} {
		if shouldRetryWshFailure(WshCheckResult{NoWshCode: code}) {
			t.Fatalf("expected %s not to retry", code)
		}
	}
}
