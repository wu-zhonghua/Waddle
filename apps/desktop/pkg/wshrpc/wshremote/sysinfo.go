// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wshremote

import (
	"context"
	"log"
	"os"
	"os/exec"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/disk"
	"github.com/shirou/gopsutil/v4/host"
	"github.com/shirou/gopsutil/v4/load"
	"github.com/shirou/gopsutil/v4/mem"
	gnet "github.com/shirou/gopsutil/v4/net"
	"github.com/shirou/gopsutil/v4/process"
	"github.com/shirou/gopsutil/v4/sensors"
	"github.com/waddledev/waddle/pkg/wps"
	"github.com/waddledev/waddle/pkg/wshrpc"
	"github.com/waddledev/waddle/pkg/wshrpc/wshclient"
	"github.com/waddledev/waddle/pkg/wshutil"
)

const BYTES_PER_GB = 1073741824
const BYTES_PER_MB = 1048576
const nvidiaSmiTimeout = 2 * time.Second

var nvidiaSmiFallbackPaths = []string{
	"/usr/bin/nvidia-smi",
	"/usr/local/bin/nvidia-smi",
	"/usr/local/nvidia/bin/nvidia-smi",
	"/opt/nvidia/bin/nvidia-smi",
}

type sysInfoCollector struct {
	prevNetBytesRecv uint64
	prevNetBytesSent uint64
	prevNetTs        time.Time
}

func getCpuData(values map[string]float64) {
	percentArr, err := cpu.Percent(0, false)
	if err != nil {
		return
	}
	if len(percentArr) > 0 {
		values[wshrpc.TimeSeries_Cpu] = percentArr[0]
	}
	percentArr, err = cpu.Percent(0, true)
	if err != nil {
		return
	}
	for idx, percent := range percentArr {
		values[wshrpc.TimeSeries_Cpu+":"+strconv.Itoa(idx)] = percent
	}
}

func getMemData(values map[string]float64) {
	memData, err := mem.VirtualMemory()
	if err != nil {
		return
	}
	values["mem:total"] = float64(memData.Total) / BYTES_PER_GB
	values["mem:available"] = float64(memData.Available) / BYTES_PER_GB
	values["mem:used"] = float64(memData.Used) / BYTES_PER_GB
	values["mem:free"] = float64(memData.Free) / BYTES_PER_GB
}

func getDiskData(values map[string]float64) {
	usage, err := disk.Usage("/")
	if err != nil {
		return
	}
	values["disk:total"] = float64(usage.Total) / BYTES_PER_GB
	values["disk:used"] = float64(usage.Used) / BYTES_PER_GB
	values["disk:free"] = float64(usage.Free) / BYTES_PER_GB
	values["disk:usedpct"] = usage.UsedPercent
}

func rateMBPerSec(prev uint64, cur uint64, elapsed time.Duration) float64 {
	if elapsed <= 0 || cur < prev {
		return 0
	}
	return (float64(cur-prev) / BYTES_PER_MB) / elapsed.Seconds()
}

func (collector *sysInfoCollector) addNetworkData(values map[string]float64, now time.Time, counters []gnet.IOCountersStat) {
	if len(counters) == 0 {
		return
	}
	current := counters[0]
	if collector.prevNetTs.IsZero() {
		values["net:download"] = 0
		values["net:upload"] = 0
	} else {
		elapsed := now.Sub(collector.prevNetTs)
		values["net:download"] = rateMBPerSec(collector.prevNetBytesRecv, current.BytesRecv, elapsed)
		values["net:upload"] = rateMBPerSec(collector.prevNetBytesSent, current.BytesSent, elapsed)
	}
	collector.prevNetBytesRecv = current.BytesRecv
	collector.prevNetBytesSent = current.BytesSent
	collector.prevNetTs = now
}

func (collector *sysInfoCollector) getNetworkData(values map[string]float64, now time.Time) {
	counters, err := gnet.IOCounters(false)
	if err != nil {
		return
	}
	collector.addNetworkData(values, now, counters)
}

func getLoadData(values map[string]float64) {
	loadData, err := load.Avg()
	if err != nil {
		return
	}
	values["load:1"] = loadData.Load1
	values["load:5"] = loadData.Load5
	values["load:15"] = loadData.Load15
}

func getProcessData(values map[string]float64) {
	pids, err := process.Pids()
	if err != nil {
		return
	}
	values["proc:count"] = float64(len(pids))
}

func getTemperatureData(values map[string]float64) {
	temps, err := sensors.SensorsTemperatures()
	if err != nil {
		return
	}
	var maxTemp float64
	for _, temp := range temps {
		if temp.Temperature > maxTemp {
			maxTemp = temp.Temperature
		}
	}
	if maxTemp > 0 {
		values["temp:max"] = maxTemp
	}
}

func getUptimeData(values map[string]float64) {
	uptime, err := host.Uptime()
	if err != nil {
		return
	}
	values["uptime:hours"] = float64(uptime) / 3600
}

func parseDarwinGPUValue(output string, name string) (float64, bool) {
	pattern := `"` + regexp.QuoteMeta(name) + `"\s*=\s*([0-9]+(?:\.[0-9]+)?)`
	match := regexp.MustCompile(pattern).FindStringSubmatch(output)
	if len(match) < 2 {
		return 0, false
	}
	value, err := strconv.ParseFloat(match[1], 64)
	if err != nil {
		return 0, false
	}
	return value, true
}

func parseDarwinGPUData(output string, values map[string]float64) bool {
	found := false
	if util, ok := parseDarwinGPUValue(output, "Device Utilization %"); ok {
		values["gpu:util"] = util
		found = true
	} else if util, ok := parseDarwinGPUValue(output, "Renderer Utilization %"); ok {
		values["gpu:util"] = util
		found = true
	}
	if memUsed, ok := parseDarwinGPUValue(output, "In use system memory"); ok {
		values["gpu:memused"] = memUsed / BYTES_PER_GB
		found = true
	}
	if memTotal, ok := parseDarwinGPUValue(output, "Alloc system memory"); ok {
		values["gpu:memtotal"] = memTotal / BYTES_PER_GB
		found = true
	}
	return found
}

func getDarwinGpuData(values map[string]float64) bool {
	if runtime.GOOS != "darwin" {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), 500*time.Millisecond)
	defer cancel()
	output, err := exec.CommandContext(ctx, "ioreg", "-r", "-c", "AGXAccelerator", "-d", "1").Output()
	if err != nil {
		return false
	}
	return parseDarwinGPUData(string(output), values)
}

func pathExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func resolveNvidiaSmiPath(
	lookPath func(string) (string, error),
	fileExists func(string) bool,
) (string, bool) {
	if nvidiaSmiPath, err := lookPath("nvidia-smi"); err == nil && nvidiaSmiPath != "" {
		return nvidiaSmiPath, true
	}
	for _, fallbackPath := range nvidiaSmiFallbackPaths {
		if fileExists(fallbackPath) {
			return fallbackPath, true
		}
	}
	return "", false
}

func parseNvidiaGPUData(output string, values map[string]float64) bool {
	lines := strings.Split(strings.TrimSpace(output), "\n")
	var gpuCount float64
	var utilTotal float64
	var memUsedTotal float64
	var memTotal float64
	for _, line := range lines {
		parts := strings.Split(line, ",")
		if len(parts) < 3 {
			continue
		}
		util, utilErr := strconv.ParseFloat(strings.TrimSpace(parts[0]), 64)
		memUsed, memUsedErr := strconv.ParseFloat(strings.TrimSpace(parts[1]), 64)
		memTotalValue, memTotalErr := strconv.ParseFloat(strings.TrimSpace(parts[2]), 64)
		if utilErr != nil || memUsedErr != nil || memTotalErr != nil {
			continue
		}
		gpuCount++
		utilTotal += util
		memUsedTotal += memUsed
		memTotal += memTotalValue
	}
	if gpuCount == 0 {
		return false
	}
	values["gpu:util"] = utilTotal / gpuCount
	values["gpu:memused"] = memUsedTotal / 1024
	values["gpu:memtotal"] = memTotal / 1024
	return true
}

func getNvidiaGpuData(values map[string]float64) {
	nvidiaSmiPath, ok := resolveNvidiaSmiPath(exec.LookPath, pathExists)
	if !ok {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), nvidiaSmiTimeout)
	defer cancel()
	output, err := exec.CommandContext(
		ctx,
		nvidiaSmiPath,
		"--query-gpu=utilization.gpu,memory.used,memory.total",
		"--format=csv,noheader,nounits",
	).Output()
	if err != nil {
		return
	}
	parseNvidiaGPUData(string(output), values)
}

func getGpuData(values map[string]float64) {
	if getDarwinGpuData(values) {
		return
	}
	getNvidiaGpuData(values)
}

func (collector *sysInfoCollector) generateSingleServerData(client *wshutil.WshRpc, connName string) {
	now := time.Now()
	values := make(map[string]float64)
	getCpuData(values)
	getMemData(values)
	getDiskData(values)
	collector.getNetworkData(values, now)
	getLoadData(values)
	getProcessData(values)
	getTemperatureData(values)
	getUptimeData(values)
	getGpuData(values)
	tsData := wshrpc.TimeSeriesData{Ts: now.UnixMilli(), Values: values}
	event := wps.WaddleEvent{
		Event:   wps.Event_SysInfo,
		Scopes:  []string{connName},
		Data:    tsData,
		Persist: 1024,
	}
	wshclient.EventPublishCommand(client, event, &wshrpc.RpcOpts{NoResponse: true})
}

func RunSysInfoLoop(client *wshutil.WshRpc, connName string) {
	defer func() {
		log.Printf("sysinfo loop ended conn:%s\n", connName)
	}()
	collector := &sysInfoCollector{}
	for {
		collector.generateSingleServerData(client, connName)
		time.Sleep(1 * time.Second)
	}
}
