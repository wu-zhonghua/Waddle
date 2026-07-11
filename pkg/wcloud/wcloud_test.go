// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wcloud

import (
	"testing"

	"github.com/waddledev/waddle/pkg/wavebase"
)

func TestWaveCloudDefaultEndpoints(t *testing.T) {
	if WCloudEndpoint != "https://api.waveterm.dev/central" {
		t.Fatalf("unexpected central endpoint %q", WCloudEndpoint)
	}
	if WCloudPingEndpoint != "https://ping.waveterm.dev/central" {
		t.Fatalf("unexpected ping endpoint %q", WCloudPingEndpoint)
	}
}

func TestWaveCloudEndpointOverridesRemainAvailableInDevMode(t *testing.T) {
	oldDev := wavebase.Dev_VarCache
	oldEndpoint := WCloudEndpoint_VarCache
	oldPing := WCloudPingEndpoint_VarCache
	t.Cleanup(func() {
		wavebase.Dev_VarCache = oldDev
		WCloudEndpoint_VarCache = oldEndpoint
		WCloudPingEndpoint_VarCache = oldPing
	})

	wavebase.Dev_VarCache = "1"
	WCloudEndpoint_VarCache = "https://central.example.test"
	WCloudPingEndpoint_VarCache = "https://ping.example.test"
	if GetEndpoint() != WCloudEndpoint_VarCache {
		t.Fatalf("central override was not preserved")
	}
	if GetPingEndpoint() != WCloudPingEndpoint_VarCache {
		t.Fatalf("ping override was not preserved")
	}
}
