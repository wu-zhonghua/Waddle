// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

package wavebase

import (
	"context"
	"errors"
	"fmt"
	"io/fs"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/waddledev/waddle/pkg/util/utilfn"
)

// set by main-server.go
var WaddleVersion = "0.0.0"
var BuildTime = "0"

const (
	WaddleConfigHomeEnvVar           = "WADDLE_CONFIG_HOME"
	WaddleDataHomeEnvVar             = "WADDLE_DATA_HOME"
	WaddleAppPathVarName             = "WADDLE_APP_PATH"
	WaddleAppResourcesPathVarName    = "WADDLE_RESOURCES_PATH"
	WaddleAppElectronExecPathVarName = "WADDLE_ELECTRONEXECPATH"
	WaddleDevVarName                 = "WADDLE_DEV"
	WaddleDevViteVarName             = "WADDLE_DEV_VITE"
	WaddleWshForceUpdateVarName      = "WADDLE_WSHFORCEUPDATE"
	WaddleNoConfirmQuitVarName       = "WADDLE_NOCONFIRMQUIT"

	WaddleJwtTokenVarName  = "WADDLE_JWT"
	WaddleSwapTokenVarName = "WADDLE_SWAPTOKEN"
)

const (
	BlockFile_Term  = "term"            // used for main pty output
	BlockFile_Cache = "cache:term:full" // for cached block
	BlockFile_VDom  = "vdom"            // used for alt html layout
	BlockFile_Env   = "env"
)

const NeedJwtConst = "NEED-JWT"

var ConfigHome_VarCache string          // caches WADDLE_CONFIG_HOME
var DataHome_VarCache string            // caches WADDLE_DATA_HOME
var AppPath_VarCache string             // caches WADDLE_APP_PATH
var AppResourcesPath_VarCache string    // caches WADDLE_RESOURCES_PATH
var AppElectronExecPath_VarCache string // caches WADDLE_ELECTRONEXECPATH
var Dev_VarCache string                 // caches WADDLE_DEV

const WaddleLockFile = "waddle.lock"
const DomainSocketBaseName = "waddle.sock"
const RemoteDomainSocketBaseName = "waddle-remote.sock"
const WaddleDBDir = "db"
const ConfigDir = "config"
const RemoteWaddleHomeDirName = ".waddle"
const RemoteWshBinDirName = "bin"
const RemoteFullWshBinPath = "~/.waddle/bin/wsh"
const RemoteFullDomainSocketPath = "~/.waddle/waddle-remote.sock"

const AppPathBinDir = "bin"

var baseLock = &sync.Mutex{}
var ensureDirCache = map[string]bool{}

var waveCachesDirOnce = &sync.Once{}
var waveCachesDir string

var SupportedWshBinaries = map[string]bool{
	"darwin-x64":    true,
	"darwin-arm64":  true,
	"linux-x64":     true,
	"linux-arm64":   true,
	"windows-x64":   true,
	"windows-arm64": true,
}

type FDLock interface {
	Close() error
}

func CacheAndRemoveEnvVars() error {
	ConfigHome_VarCache = os.Getenv(WaddleConfigHomeEnvVar)
	if ConfigHome_VarCache == "" {
		return fmt.Errorf(WaddleConfigHomeEnvVar + " not set")
	}
	os.Unsetenv(WaddleConfigHomeEnvVar)
	DataHome_VarCache = os.Getenv(WaddleDataHomeEnvVar)
	if DataHome_VarCache == "" {
		return fmt.Errorf("%s not set", WaddleDataHomeEnvVar)
	}
	os.Unsetenv(WaddleDataHomeEnvVar)
	AppPath_VarCache = os.Getenv(WaddleAppPathVarName)
	os.Unsetenv(WaddleAppPathVarName)
	AppResourcesPath_VarCache = os.Getenv(WaddleAppResourcesPathVarName)
	os.Unsetenv(WaddleAppResourcesPathVarName)
	AppElectronExecPath_VarCache = os.Getenv(WaddleAppElectronExecPathVarName)
	os.Unsetenv(WaddleAppElectronExecPathVarName)
	Dev_VarCache = os.Getenv(WaddleDevVarName)
	os.Unsetenv(WaddleDevVarName)
	os.Unsetenv(WaddleDevViteVarName)
	os.Unsetenv(WaddleNoConfirmQuitVarName)
	return nil
}

func IsDevMode() bool {
	return Dev_VarCache != ""
}

func GetWaddleAppPath() string {
	return AppPath_VarCache
}

func GetWaddleAppResourcesPath() string {
	return AppResourcesPath_VarCache
}

func GetWaddleDataDir() string {
	return DataHome_VarCache
}

func GetWaddleConfigDir() string {
	return ConfigHome_VarCache
}

func GetWaddleAppBinPath() string {
	return filepath.Join(GetWaddleAppPath(), AppPathBinDir)
}

func GetWaddleAppElectronExecPath() string {
	return AppElectronExecPath_VarCache
}

func GetHomeDir() string {
	homeVar, err := os.UserHomeDir()
	if err != nil {
		return "/"
	}
	return homeVar
}

func ExpandHomeDir(pathStr string) (string, error) {
	if pathStr != "~" && !strings.HasPrefix(pathStr, "~/") && (!strings.HasPrefix(pathStr, `~\`) || runtime.GOOS != "windows") {
		return filepath.Clean(pathStr), nil
	}
	homeDir := GetHomeDir()
	if pathStr == "~" {
		return homeDir, nil
	}
	expandedPath := filepath.Clean(filepath.Join(homeDir, pathStr[2:]))
	absPath, err := filepath.Abs(filepath.Join(homeDir, expandedPath))
	if err != nil || !strings.HasPrefix(absPath, homeDir) {
		return "", fmt.Errorf("potential path traversal detected for path %s", pathStr)
	}
	return expandedPath, nil
}

func ExpandHomeDirSafe(pathStr string) string {
	path, _ := ExpandHomeDir(pathStr)
	return path
}

func ReplaceHomeDir(pathStr string) string {
	homeDir := GetHomeDir()
	if pathStr == homeDir {
		return "~"
	}
	if strings.HasPrefix(pathStr, homeDir+"/") {
		return "~" + pathStr[len(homeDir):]
	}
	return pathStr
}

func GetDomainSocketName() string {
	return filepath.Join(GetWaddleDataDir(), DomainSocketBaseName)
}

// returns a Unix-style path for the remote socket (using fmt.Sprintf instead of filepath.Join
// because this path is for a remote Unix system, not the local OS which might be Windows)
func GetPersistentRemoteSockName(clientId string) string {
	return fmt.Sprintf("~/.waddle/client/%s/waddle.sock", clientId)
}

func EnsureWaddleDataDir() error {
	return CacheEnsureDir(GetWaddleDataDir(), "waddlehome", 0700, "Waddle home directory")
}

func EnsureWaddleDBDir() error {
	return CacheEnsureDir(filepath.Join(GetWaddleDataDir(), WaddleDBDir), "waddledb", 0700, "Waddle db directory")
}

func EnsureWaddleConfigDir() error {
	return CacheEnsureDir(GetWaddleConfigDir(), "waveconfig", 0700, "Waddle config directory")
}

func EnsureWaddlePresetsDir() error {
	return CacheEnsureDir(filepath.Join(GetWaddleConfigDir(), "presets"), "waddlepresets", 0700, "Waddle presets directory")
}

func resolveWaddleCachesDir() string {
	var cacheDir string
	appBundle := "waddle"
	if IsDevMode() {
		appBundle = "waddle-dev"
	}

	switch runtime.GOOS {
	case "darwin":
		homeDir := GetHomeDir()
		cacheDir = filepath.Join(homeDir, "Library", "Caches", appBundle)
	case "linux":
		xdgCache := os.Getenv("XDG_CACHE_HOME")
		if xdgCache != "" {
			cacheDir = filepath.Join(xdgCache, appBundle)
		} else {
			homeDir := GetHomeDir()
			cacheDir = filepath.Join(homeDir, ".cache", appBundle)
		}
	case "windows":
		localAppData := os.Getenv("LOCALAPPDATA")
		if localAppData != "" {
			cacheDir = filepath.Join(localAppData, appBundle, "Cache")
		}
	}

	if cacheDir == "" {
		tmpDir := os.TempDir()
		cacheDir = filepath.Join(tmpDir, appBundle)
	}

	return cacheDir
}

func GetWaddleCachesDir() string {
	waveCachesDirOnce.Do(func() {
		waveCachesDir = resolveWaddleCachesDir()
	})
	return waveCachesDir
}

func EnsureWaddleCachesDir() error {
	return CacheEnsureDir(GetWaddleCachesDir(), "waddlecaches", 0700, "Waddle caches directory")
}

func CacheEnsureDir(dirName string, cacheKey string, perm os.FileMode, dirDesc string) error {
	baseLock.Lock()
	ok := ensureDirCache[cacheKey]
	baseLock.Unlock()
	if ok {
		return nil
	}
	err := TryMkdirs(dirName, perm, dirDesc)
	if err != nil {
		return err
	}
	baseLock.Lock()
	ensureDirCache[cacheKey] = true
	baseLock.Unlock()
	return nil
}

func TryMkdirs(dirName string, perm os.FileMode, dirDesc string) error {
	info, err := os.Stat(dirName)
	if errors.Is(err, fs.ErrNotExist) {
		err = os.MkdirAll(dirName, perm)
		if err != nil {
			return fmt.Errorf("cannot make %s %q: %w", dirDesc, dirName, err)
		}
		info, err = os.Stat(dirName)
	}
	if err != nil {
		return fmt.Errorf("error trying to stat %s: %w", dirDesc, err)
	}
	if !info.IsDir() {
		return fmt.Errorf("%s %q must be a directory", dirDesc, dirName)
	}
	return nil
}

func listValidLangs(ctx context.Context) []string {
	out, err := exec.CommandContext(ctx, "locale", "-a").CombinedOutput()
	if err != nil {
		log.Printf("error running 'locale -a': %s\n", err)
		return []string{}
	}
	// don't bother with CRLF line endings
	// this command doesn't work on windows
	return strings.Split(string(out), "\n")
}

var osLangOnce = &sync.Once{}
var osLang string

func determineLang() string {
	defaultLang := "en_US.UTF-8"
	ctx, cancelFn := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancelFn()
	if runtime.GOOS == "darwin" {
		out, err := exec.CommandContext(ctx, "defaults", "read", "-g", "AppleLocale").CombinedOutput()
		if err != nil {
			log.Printf("error executing 'defaults read -g AppleLocale', will use default 'en_US.UTF-8': %v\n", err)
			return defaultLang
		}
		strOut := string(out)
		truncOut := strings.Split(strOut, "@")[0]
		preferredLang := strings.TrimSpace(truncOut) + ".UTF-8"
		validLangs := listValidLangs(ctx)

		if !utilfn.ContainsStr(validLangs, preferredLang) {
			log.Printf("unable to use desired lang %s, will use default 'en_US.UTF-8'\n", preferredLang)
			return defaultLang
		}

		return preferredLang
	} else {
		// this is specifically to get the wavesrv LANG so waveshell
		// on a remote uses the same LANG
		return os.Getenv("LANG")
	}
}

func DetermineLang() string {
	osLangOnce.Do(func() {
		osLang = determineLang()
	})
	return osLang
}

func DetermineLocale() string {
	truncated := strings.Split(DetermineLang(), ".")[0]
	if truncated == "" {
		return "C"
	}
	return strings.Replace(truncated, "_", "-", -1)
}

func ClientArch() string {
	return fmt.Sprintf("%s/%s", runtime.GOOS, runtime.GOARCH)
}

func ClientPackageType() string {
	if os.Getenv("SNAP") != "" {
		return "snap"
	}
	if os.Getenv("APPIMAGE") != "" {
		return "appimage"
	}
	return ""
}

var macOSVersionOnce = &sync.Once{}
var cachedMacOSVersion string

var macOSVersionRegex = regexp.MustCompile(`^(\d+\.\d+(?:\.\d+)?)`)

func internalMacOSVersion() string {
	ctx, cancelFn := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancelFn()
	out, err := exec.CommandContext(ctx, "sw_vers", "-productVersion").Output()
	if err != nil {
		return ""
	}
	versionStr := strings.TrimSpace(string(out))
	m := macOSVersionRegex.FindStringSubmatch(versionStr)
	if len(m) < 2 {
		return ""
	}
	return m[1]
}

func ClientMacOSVersion() string {
	if runtime.GOOS != "darwin" {
		return ""
	}
	macOSVersionOnce.Do(func() {
		cachedMacOSVersion = internalMacOSVersion()
	})
	return cachedMacOSVersion
}

var releaseRegex = regexp.MustCompile(`^(\d+\.\d+\.\d+)`)
var osReleaseOnce = &sync.Once{}
var osRelease string

func unameKernelRelease() string {
	if runtime.GOOS == "windows" {
		return "-"
	}
	ctx, cancelFn := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancelFn()
	out, err := exec.CommandContext(ctx, "uname", "-r").CombinedOutput()
	if err != nil {
		log.Printf("error executing uname -r: %v\n", err)
		return "-"
	}
	releaseStr := strings.TrimSpace(string(out))
	m := releaseRegex.FindStringSubmatch(releaseStr)
	if len(m) < 2 {
		log.Printf("invalid uname -r output: [%s]\n", releaseStr)
		return "-"
	}
	return m[1]
}

func UnameKernelRelease() string {
	osReleaseOnce.Do(func() {
		osRelease = unameKernelRelease()
	})
	return osRelease
}

var systemSummaryOnce = &sync.Once{}
var systemSummary string

func GetSystemSummary() string {
	systemSummaryOnce.Do(func() {
		ctx, cancelFn := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancelFn()
		systemSummary = getSystemSummary(ctx)
	})
	return systemSummary
}

func ValidateWshSupportedArch(os string, arch string) error {
	if SupportedWshBinaries[fmt.Sprintf("%s-%s", os, arch)] {
		return nil
	}
	return fmt.Errorf("unsupported wsh platform: %s-%s", os, arch)
}

func getSystemSummary(ctx context.Context) string {
	osName := runtime.GOOS

	switch osName {
	case "darwin":
		out, _ := exec.CommandContext(ctx, "sw_vers", "-productVersion").Output()
		return fmt.Sprintf("macOS %s (%s)", strings.TrimSpace(string(out)), runtime.GOARCH)
	case "linux":
		// Read /etc/os-release directly (standard location since 2012)
		data, err := os.ReadFile("/etc/os-release")
		var prettyName string
		if err == nil {
			for _, line := range strings.Split(string(data), "\n") {
				line = strings.TrimSpace(line)
				if strings.HasPrefix(line, "PRETTY_NAME=") {
					prettyName = strings.Trim(strings.TrimPrefix(line, "PRETTY_NAME="), "\"")
					break
				}
			}
		}
		if prettyName == "" {
			prettyName = "Linux"
		} else if !strings.Contains(strings.ToLower(prettyName), "linux") {
			prettyName = "Linux " + prettyName
		}
		return fmt.Sprintf("%s (%s)", prettyName, runtime.GOARCH)
	case "windows":
		var details string
		out, err := exec.CommandContext(ctx, "powershell", "-NoProfile", "-NonInteractive", "-Command", "(Get-CimInstance Win32_OperatingSystem).Caption").Output()
		if err == nil && len(out) > 0 {
			details = strings.TrimSpace(string(out))
		} else {
			details = "Windows"
		}
		return fmt.Sprintf("%s (%s)", details, runtime.GOARCH)
	default:
		return fmt.Sprintf("%s (%s)", runtime.GOOS, runtime.GOARCH)
	}
}

// job socket path on remote machine
func GetRemoteJobSocketPath(jobId string) string {
	socketDir := filepath.Join("/tmp", fmt.Sprintf("waddle-%d", os.Getuid()))
	return filepath.Join(socketDir, fmt.Sprintf("%s.sock", jobId))
}

// job file path on remote machine
func GetRemoteJobFilePath(jobId string, extension string) string {
	jobDir := GetRemoteJobLogDir()
	return filepath.Join(jobDir, fmt.Sprintf("%s.%s", jobId, extension))
}

// job file dir on remote machines
func GetRemoteJobLogDir() string {
	homeDir := GetHomeDir()
	jobDir := filepath.Join(homeDir, ".waddle", "jobs")
	return jobDir
}
