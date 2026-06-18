// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package remote

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"log"
	"os"
	"os/user"
	"path/filepath"
	"regexp"
	"strings"
	"text/template"
	"time"

	"github.com/waddledev/waddle/pkg/blocklogger"
	"github.com/waddledev/waddle/pkg/genconn"
	"github.com/waddledev/waddle/pkg/util/iterfn"
	"github.com/waddledev/waddle/pkg/util/shellutil"
	"github.com/waddledev/waddle/pkg/wavebase"
	"github.com/waddledev/waddle/pkg/wconfig"
	"github.com/waddledev/waddle/pkg/wps"
	"golang.org/x/crypto/ssh"
)

const wshInstallProgressStepPercent int64 = 1

var userHostRe = regexp.MustCompile(`^([a-zA-Z0-9][a-zA-Z0-9._@\\-]*@)?([a-zA-Z0-9][a-zA-Z0-9.-]*)(?::([0-9]+))?$`)

type WshInstallProgressWriter struct {
	dst         io.Writer
	connName    string
	total       int64
	written     int64
	nextPercent int64
	publishf    func(wps.WshInstallProgressData)
}

func makeWshInstallProgressWriter(
	dst io.Writer,
	total int64,
	connName string,
	_ func(string, ...any),
	publishf func(wps.WshInstallProgressData),
) *WshInstallProgressWriter {
	return &WshInstallProgressWriter{
		dst:         dst,
		connName:    connName,
		total:       total,
		nextPercent: wshInstallProgressStepPercent,
		publishf:    publishf,
	}
}

func formatInstallByteCount(size int64) string {
	const kib = 1024
	const mib = kib * 1024
	if size < kib {
		return fmt.Sprintf("%d B", size)
	}
	if size < mib {
		return fmt.Sprintf("%.1f KiB", float64(size)/kib)
	}
	return fmt.Sprintf("%.1f MiB", float64(size)/mib)
}

func (w *WshInstallProgressWriter) publishProgress(status string, percent int64, message string) {
	if w.publishf == nil || w.connName == "" {
		return
	}
	if percent < 0 {
		percent = 0
	}
	if percent > 100 {
		percent = 100
	}
	w.publishf(wps.WshInstallProgressData{
		ConnName: w.connName,
		Status:   status,
		Percent:  int(percent),
		Written:  w.written,
		Total:    w.total,
		Message:  message,
	})
}

func (w *WshInstallProgressWriter) Start() {
	w.publishProgress("running", 0, "")
}

func (w *WshInstallProgressWriter) Write(p []byte) (int, error) {
	n, err := w.dst.Write(p)
	if n <= 0 || w.total <= 0 {
		return n, err
	}
	w.written += int64(n)
	percent := w.written * 100 / w.total
	if percent > 100 {
		percent = 100
	}
	if percent >= w.nextPercent {
		w.publishProgress("running", percent, "")
		w.nextPercent = ((percent / wshInstallProgressStepPercent) + 1) * wshInstallProgressStepPercent
	}
	return n, err
}

func (w *WshInstallProgressWriter) Finish() {
	if w.total > 0 {
		w.written = w.total
	}
	w.publishProgress("done", 100, "")
}

func (w *WshInstallProgressWriter) Fail(err error) {
	message := ""
	if err != nil {
		message = err.Error()
	}
	percent := int64(0)
	if w.total > 0 {
		percent = w.written * 100 / w.total
	}
	w.publishProgress("error", percent, message)
}

func publishWshInstallProgress(data wps.WshInstallProgressData) {
	wps.Broker.Publish(wps.WaddleEvent{
		Event: wps.Event_WshInstallProgress,
		Data:  data,
	})
}

func ParseOpts(input string) (*SSHOpts, error) {
	m := userHostRe.FindStringSubmatch(input)
	if m == nil {
		return nil, fmt.Errorf("invalid format of user@host argument")
	}
	remoteUser, remoteHost, remotePort := m[1], m[2], m[3]
	remoteUser = strings.Trim(remoteUser, "@")

	return &SSHOpts{SSHHost: remoteHost, SSHUser: remoteUser, SSHPort: remotePort}, nil
}

func normalizeOs(os string) string {
	os = strings.ToLower(strings.TrimSpace(os))
	return os
}

func normalizeArch(arch string) string {
	arch = strings.ToLower(strings.TrimSpace(arch))
	switch arch {
	case "x86_64", "amd64":
		arch = "x64"
	case "arm64", "aarch64":
		arch = "arm64"
	}
	return arch
}

// returns (os, arch, error)
// guaranteed to return a supported platform
func GetClientPlatform(ctx context.Context, shell genconn.ShellClient) (string, string, error) {
	blocklogger.Infof(ctx, "[conndebug] running `uname -sm` to detect client platform\n")
	stdout, stderr, err := genconn.RunSimpleCommand(ctx, shell, genconn.CommandSpec{
		Cmd: "uname -sm",
	})
	if err != nil {
		return "", "", fmt.Errorf("error running uname -sm: %w, stderr: %s", err, stderr)
	}
	// Parse and normalize output
	parts := strings.Fields(strings.ToLower(strings.TrimSpace(stdout)))
	if len(parts) != 2 {
		return "", "", fmt.Errorf("unexpected output from uname: %s", stdout)
	}
	os, arch := normalizeOs(parts[0]), normalizeArch(parts[1])
	if err := wavebase.ValidateWshSupportedArch(os, arch); err != nil {
		return "", "", err
	}
	return os, arch, nil
}

func GetClientPlatformFromOsArchStr(ctx context.Context, osArchStr string) (string, string, error) {
	parts := strings.Fields(strings.TrimSpace(osArchStr))
	if len(parts) != 2 {
		return "", "", fmt.Errorf("unexpected output from uname: %s", osArchStr)
	}
	os, arch := normalizeOs(parts[0]), normalizeArch(parts[1])
	if err := wavebase.ValidateWshSupportedArch(os, arch); err != nil {
		return "", "", err
	}
	return os, arch, nil
}

var installTemplateRawDefault = strings.TrimSpace(`
mkdir -p {{.installDir}} || exit 1;
cat > {{.tempPath}} || exit 1;
mv {{.tempPath}} {{.installPath}} || exit 1;
chmod a+x {{.installPath}} || exit 1;
`)
var installTemplate = template.Must(template.New("wsh-install-template").Parse(installTemplateRawDefault))

func CpWshToRemote(ctx context.Context, client *ssh.Client, connName string, clientOs string, clientArch string) error {
	deadline, ok := ctx.Deadline()
	if ok {
		blocklogger.Debugf(ctx, "[conndebug] CpWshToRemote, timeout: %v\n", time.Until(deadline))
	}
	wshLocalPath, err := shellutil.GetLocalWshBinaryPath(wavebase.WaddleVersion, clientOs, clientArch)
	if err != nil {
		return err
	}
	input, err := os.Open(wshLocalPath)
	if err != nil {
		return fmt.Errorf("cannot open local file %s: %w", wshLocalPath, err)
	}
	defer input.Close()
	inputInfo, err := input.Stat()
	if err != nil {
		return fmt.Errorf("cannot stat local file %s: %w", wshLocalPath, err)
	}
	installWords := map[string]string{
		"installDir":  filepath.ToSlash(filepath.Dir(wavebase.RemoteFullWshBinPath)),
		"tempPath":    wavebase.RemoteFullWshBinPath + ".temp",
		"installPath": wavebase.RemoteFullWshBinPath,
	}
	var installCmd bytes.Buffer
	if err := installTemplate.Execute(&installCmd, installWords); err != nil {
		return fmt.Errorf("failed to prepare install command: %w", err)
	}
	blocklogger.Infof(ctx, "[conndebug] copying %q to remote server %q\n", wshLocalPath, wavebase.RemoteFullWshBinPath)
	genCmd, err := genconn.MakeSSHCmdClient(client, genconn.CommandSpec{
		Cmd: installCmd.String(),
	})
	if err != nil {
		return fmt.Errorf("failed to create remote command: %w", err)
	}
	stdin, err := genCmd.StdinPipe()
	if err != nil {
		return fmt.Errorf("failed to get stdin pipe: %w", err)
	}
	defer stdin.Close()
	stderrBuf, err := genconn.MakeStderrSyncBuffer(genCmd)
	if err != nil {
		return fmt.Errorf("failed to get stderr pipe: %w", err)
	}
	if err := genCmd.Start(); err != nil {
		return fmt.Errorf("failed to start remote command: %w", err)
	}
	copyDone := make(chan error, 1)
	go func() {
		defer close(copyDone)
		defer stdin.Close()
		progressWriter := makeWshInstallProgressWriter(
			stdin,
			inputInfo.Size(),
			connName,
			nil,
			publishWshInstallProgress,
		)
		progressWriter.Start()
		if _, err := io.Copy(progressWriter, input); err != nil && err != io.EOF {
			progressWriter.Fail(err)
			copyDone <- fmt.Errorf("failed to copy data: %w", err)
		} else {
			progressWriter.Finish()
			copyDone <- nil
		}
	}()
	procErr := genconn.ProcessContextWait(ctx, genCmd)
	if procErr != nil {
		publishWshInstallProgress(wps.WshInstallProgressData{
			ConnName: connName,
			Status:   "error",
			Total:    inputInfo.Size(),
			Message:  procErr.Error(),
		})
		return fmt.Errorf("remote command failed: %w (stderr: %s)", procErr, stderrBuf.String())
	}
	copyErr := <-copyDone
	if copyErr != nil {
		return fmt.Errorf("failed to copy data: %w (stderr: %s)", copyErr, stderrBuf.String())
	}
	return nil
}

func IsPowershell(shellPath string) bool {
	// get the base path, and then check contains
	shellBase := filepath.Base(shellPath)
	return strings.Contains(shellBase, "powershell") || strings.Contains(shellBase, "pwsh")
}

func NormalizeConfigPattern(pattern string) string {
	userName, err := WaddleSshConfigUserSettings().GetStrict(pattern, "User")
	if err != nil || userName == "" {
		log.Printf("warning: error parsing username of %s for conn dropdown: %v", pattern, err)
		localUser, err := user.Current()
		if err == nil {
			userName = localUser.Username
		}
	}
	port, err := WaddleSshConfigUserSettings().GetStrict(pattern, "Port")
	if err != nil {
		port = "22"
	}
	if userName != "" {
		userName += "@"
	}
	if port == "22" {
		port = ""
	} else {
		port = ":" + port
	}
	return fmt.Sprintf("%s%s%s", userName, pattern, port)
}

func ParseProfiles() []string {
	connfile, cerrs := wconfig.ReadWaddleHomeConfigFile(wconfig.ProfilesFile)
	if len(cerrs) > 0 {
		log.Printf("error reading config file: %v", cerrs[0])
		return nil
	}

	return iterfn.MapKeysToSorted(connfile)
}
