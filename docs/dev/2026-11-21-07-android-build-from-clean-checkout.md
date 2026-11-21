# 从干净的仓库能构建安卓版，GitHub 上的 Android 检查通过

- 状态：待实现
- 日期：2026-11-21
- 上一份：[2026-11-19-01](2026-11-19-01-codex-in-waddle.md)、[2026-11-19-02](2026-11-19-02-terminal-ai-command.md)、[2026-11-20-03](2026-11-20-03-file-browser.md)、[2026-11-21-01](2026-11-21-01-files-drawer.md)、[2026-11-21-02](2026-11-21-02-open-terminal-here.md)、[2026-11-21-03](2026-11-21-03-open-codex-here.md)、[2026-11-21-04](2026-11-21-04-file-locations.md)
- 涉及：安卓（构建和 GitHub 检查）

## 现在的样子

上面七份需求一起提交（`2a2aa8ba`）后，GitHub 上的 [Android 检查失败](https://github.com/wu-zhonghua/Waddle/actions/runs/44891364506)：build 和 device-tests 都在「准备内置运行时」这一步就停了，后面的构建和设备测试根本没有跑。原因是构建要下载的那批 Termux 软件包（内置 Codex 0.170.0、Bash、Git 等）在软件源上已经被删了，返回 404。Mac mini 上能构建，只是因为本地缓存里还留着这些包；在 Mac mini 上用干净的仓库副本跑同一步，也是 404。

所以现在只有那台 Mac mini 能做出安卓包。换一台电脑、缓存被清掉，或者 GitHub 上，都构建不出来，CI 也就看不住安卓端。桌面端的检查是通过的。

## 要做什么

任何人从仓库拿一份干净的代码，按 README 的步骤，就能构建出和现在一样的 Waddle 安卓版：内置的 Codex 和工具和现在一样能用，用户手机上的数据和登录不受影响。上游软件源删掉旧版本以后，构建也不能跟着坏掉。GitHub 上的 Android 检查重新跑通，包括设备测试。

验收时，设备测试里那项整套运行时偶尔失败、单独跑能过的导航测试（`folderCodexPanelsKeepDirectoryDraftAndMessagesAcrossSwitchAndRecreation`）也要稳定通过。

## 不做

- 不升级内置 Codex 或其他工具的版本，除非不升级就做不到；要升级的话，在实现说明里写清楚为什么、换成了哪个版本，以及真机上重新验证了什么。
- 不改应用功能和界面。
- 不发布版本，不打 tag。

## 怎样算做好

- GitHub 上 `main` 的 Android 检查（build 和 device-tests）通过，Desktop checks 照常通过。连续跑两次（比如重新运行一次）都通过。
- 在一台没有构建过 Waddle 的电脑上（或者 Mac mini 上一个干净的仓库副本、没有任何本地缓存），按 README 的步骤能构建出安卓包。
- 这样构建出来的包装到 API 35 模拟器上，终端、Codex 页、文件页都能打开，内置 Codex 能启动；设备测试通过。
- 覆盖安装到 Fold7 主用户，保留文件和 Codex 登录，不产生分身。
- 仓库里不放构建产物、本地 SDK 路径和凭据。

---

## 实现说明（实现者填写）

<做了什么、怎么跑起来试用、有什么没做完或需要知道的。>

## 验收记录（验收者填写）

- <逐条写：做到 / 没做到 / 需要用户在手机或某台电脑上看，附看到的情况>
- CI：<Android、Desktop checks 的结果>
- 结论：
