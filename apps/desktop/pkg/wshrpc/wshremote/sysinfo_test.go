// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshremote

import (
	"errors"
	"testing"
	"time"

	gnet "github.com/shirou/gopsutil/v4/net"
)

func TestRateMBPerSec(t *testing.T) {
	rate := rateMBPerSec(1024*1024, 3*1024*1024, 2*time.Second)
	if rate != 1 {
		t.Fatalf("expected 1 MB/s, got %f", rate)
	}
}

func TestSysInfoCollectorAddsNetworkSpeed(t *testing.T) {
	collector := &sysInfoCollector{}

	firstValues := map[string]float64{}
	collector.addNetworkData(firstValues, time.UnixMilli(1000), []gnet.IOCountersStat{{
		BytesRecv: 1000,
		BytesSent: 2000,
	}})
	if firstValues["net:download"] != 0 || firstValues["net:upload"] != 0 {
		t.Fatalf("first network sample should start at zero speed, got %#v", firstValues)
	}

	secondValues := map[string]float64{}
	collector.addNetworkData(secondValues, time.UnixMilli(3000), []gnet.IOCountersStat{{
		BytesRecv: 1000 + 4*1024*1024,
		BytesSent: 2000 + 2*1024*1024,
	}})
	if secondValues["net:download"] != 2 {
		t.Fatalf("expected 2 MB/s download, got %f", secondValues["net:download"])
	}
	if secondValues["net:upload"] != 1 {
		t.Fatalf("expected 1 MB/s upload, got %f", secondValues["net:upload"])
	}
}

func TestParseDarwinGPUData(t *testing.T) {
	values := map[string]float64{}
	ok := parseDarwinGPUData(`"PerformanceStatistics" = {"Alloc system memory"=6483771392,"Renderer Utilization %"=89,"Device Utilization %"=87,"In use system memory"=1549107200}`, values)
	if !ok {
		t.Fatalf("expected darwin gpu data to parse")
	}
	if values["gpu:util"] != 87 {
		t.Fatalf("expected gpu util 87, got %f", values["gpu:util"])
	}
	if values["gpu:memused"] <= 1.4 || values["gpu:memused"] >= 1.5 {
		t.Fatalf("expected gpu memory used around 1.44 GB, got %f", values["gpu:memused"])
	}
	if values["gpu:memtotal"] <= 6 || values["gpu:memtotal"] >= 6.1 {
		t.Fatalf("expected gpu memory total around 6.04 GB, got %f", values["gpu:memtotal"])
	}
}

func TestParseNvidiaGPUData(t *testing.T) {
	values := map[string]float64{}
	ok := parseNvidiaGPUData("0, 45, 32607\n", values)
	if !ok {
		t.Fatalf("expected nvidia gpu data to parse")
	}
	if values["gpu:util"] != 0 {
		t.Fatalf("expected gpu util 0, got %f", values["gpu:util"])
	}
	if values["gpu:memused"] <= 0.043 || values["gpu:memused"] >= 0.045 {
		t.Fatalf("expected gpu memory used around 0.044 GB, got %f", values["gpu:memused"])
	}
	if values["gpu:memtotal"] <= 31.8 || values["gpu:memtotal"] >= 31.9 {
		t.Fatalf("expected gpu memory total around 31.84 GB, got %f", values["gpu:memtotal"])
	}
}

func TestResolveNvidiaSmiPathFallsBackToCommonInstallLocations(t *testing.T) {
	path, ok := resolveNvidiaSmiPath(
		func(_ string) (string, error) {
			return "", errors.New("not in path")
		},
		func(path string) bool {
			return path == "/usr/local/nvidia/bin/nvidia-smi"
		},
	)

	if !ok {
		t.Fatalf("expected nvidia-smi fallback path to resolve")
	}
	if path != "/usr/local/nvidia/bin/nvidia-smi" {
		t.Fatalf("expected /usr/local/nvidia/bin/nvidia-smi, got %q", path)
	}
}
