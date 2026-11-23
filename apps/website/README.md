# Waddle 全平台官网

独立静态官网。构建只需要 Node.js 20+，没有第三方依赖，不需要 `npm install`，不依赖安卓或桌面端工程。没有部署配置、发布流程、分析统计或远程字体。

## 本地预览

在仓库根目录执行：

```sh
cd apps/website
npm run preview
```

打开 `http://127.0.0.1:4173`。启动时自动构建国际版，默认英文；顶部 English / 中文在 `index.html` 与 `zh.html` 间切换，不需要 JavaScript。修改源码后重启预览命令、刷新页面。Ctrl-C 停止预览。

国内版在另一个终端运行：

```sh
cd apps/website
npm run preview:domestic
```

打开 `http://127.0.0.1:4174`。两个预览都只监听本机回环地址，不对外开放。

仅生成静态产物、执行检查：

```sh
npm run build             # dist/international，默认发行版
npm run build:domestic    # dist/domestic
npm test
```

也可以直接用浏览器打开对应目录中的 `index.html`。两个发行版互不覆盖，生成的 `dist/` 已忽略，不提交。构建只复制该版本实际使用的图片，不携带文案源码或另一版本的页面。

## 改文案、设计与截图

- `src/content.mjs`：每个文案键下并排维护 `en`、`zh`；页面标题、描述、图片替代文字与说明也在这里。国际版专有段落和图片在构建时选择，国内版不靠 CSS 隐藏内容。
- `src/render.mjs`：顶部、双端首屏、按电脑/手机分组的功能、下载和底部；语义结构、线性 SVG 图标、GitHub/许可证地址。下载区是状态文本；唯一主要操作跳到下载区，没有假下载按钮。
- `src/tokens.css`：颜色、系统字体、20/16/14/12 字号、4dp 间距单位、48dp 操作目标、8/12dp 圆角、24dp/1.8dp 图标参数的唯一入口。每组注释标明对应 [Android 设计规范](../../docs/dev/android-design-system.md) 的章节。网页用 `rem` 对应规范数值并尊重浏览器文字缩放。
- `src/styles.css`：布局使用这些变量。840px 对应 App 宽屏导航断点，窄屏按正文、图片的阅读顺序堆叠；479px 以下收紧外边距。规范更新时先改 tokens；若规范改变布局规则，再调整本文件。
- `assets/screenshots/`：安卓图片从 `docs/dev/screenshots/2026-11-23-02-after-*.png` **原样复制**；桌面图片来自本次在 Mac 上实际运行的 Waddle。均未裁剪、重绘或修图。`assets/screenshots.json` 逐张记录来源和尺寸，桌面新图还固定 SHA-256、捕获方式与审查记录。点击图片查看原图。中英文页面共用桌面英文/安卓中文截图，图注已说明。

更换截图时，把新的真实截图复制到官网目录，更新 `assets/screenshots.json` 的来源与尺寸，以及 `src/content.mjs` 的 `editions` 映射和对应说明。不要改动需求 02 的截图。图片中不要带真实账号、Key 或私人文件。国内版要逐张目视检查：现有国内账号截图中仍写有海外兼容协议名，因此官网改用国内设置页的「账号」入口截图；展开终端截图两版共用，里面没有海外服务内容。

终端示例展示命令，不是在线终端；SSH 示例中的 `demo-host` 是虚构主机。后续工具明确标为未包含；文案按已存在的两端能力组织。国内版更新图片名单时也要更新 `tests/build.test.mjs` 的已审查名单。

## 验证

`npm test` 校验双语页面、站内链接/锚点、图片尺寸和原图一致性、下载区无操作链接、国内产物无海外服务文字及图片隔离、重建清理与版本互不覆盖。

浏览器验收：在 375px 和 1280px 以上宽度分别检查两版，English → 中文 → English，检查整页文案和图注、下载锚点、无横向溢出、图片原比例、键盘焦点与原图链接。放大文字时也应完整可读。自动构建检查不能代替这一步。

2026-11-23 本轮：macOS 26.3.1 ARM64 / Node.js v25.9.0，`npm test` 6 项通过，两版构建通过。经用户明确授权，以本机隔离 Chrome + CDP 检查两版 × 两语言 × 375×667、390×844、1280×800、1440×900，共 16 组：两张首屏图片完整可见、无横向溢出、图片比例正确、语言往返和下载锚点正常，国内文字检查通过。1440px 下 200% 字号无横向溢出；另逐张目视审查国内图片。原生界面工具和侧边浏览器不可用，已通过本机 CDP 完成浏览器验证。未部署或发布。

## 产品页参考与这次排法

按用户补充要求，于 2026-11-23 搜索并阅读官方产品页：

| 参考 | 本页采用的思路 |
|---|---|
| [Warp Terminal](https://www.warp.dev/terminal) | 产品定位、平台与主要操作集中在首屏；先解释任务，再展开功能。 |
| [Wave Terminal](https://www.waveterm.dev/) | 用真实工作区说明终端、文件、AI 和布局之间的关系，按具体工作任务展开。 |
| [Termius](https://termius.com/) | 把跨平台支持放在首屏明显位置，电脑与手机都是产品主体。 |

这是信息组织参考，不是功能来源。没有借用竞品图片、下载链接、用户数字、服务承诺或上游免费额度。首屏改为简短居中文案与较大的双端截图，长图注合并；功能明确分为电脑和手机，并增加页内跳转。颜色、字号体系、线性图标、间距和圆角沿用 03；只新增一个基于 4dp 单位的首屏媒体宽度变量，不添加渐变或发光。

## 桌面截图来源与重拍

两张 PNG 都是仓库桌面源码实际运行的 **Waddle 0.14.16 / Electron 41.1.0 / macOS 26.3.1 ARM64**，2026-11-23 经回环 CDP `Page.captureScreenshot` 获取，原始尺寸 **2560×1520**。是 Electron 应用的内容区原图，不是 HTML 仿制。当前桌面外观尚未按安卓规范重做，本次保留真实差异。

- `desktop-workspace.png`：本地 Bash + Markdown 预览。所有文件都在本次生成的 `/tmp/waddle-website-04/demo`，提示符为 `demo $`，本机连接显示名为 `Local`。
- `desktop-ai.png`：同一工作区打开 AI 面板，选择 `Local model`。响应来自仅监听回环地址的确定性协议测试服务，图中明确写着不是模型推理；无真实账号或 Key。它证明实际面板和请求链路，不证明某一模型的回答质量。
- 国际/国内共用这两张桌面图。逐张审查没有真实账号、Key、私人文件、真实服务器地址或海外 AI 服务名；安卓图片继续使用各自发行版已审查资源。国内构建不会复制国际版专属图片或任何演示脚本。

重拍步骤（截图辅助脚本要求 Node.js 22+；官网构建仍只需 Node.js 20+；不修改桌面跟踪文件）：

1. 在官网目录执行 `node scripts/desktop-demo.mjs prepare`，记下打印的临时目录。下面把该目录记为 `DEMO_DIR`，不要用个人配置或个人文件。
2. 桌面端已有 Node 依赖，且 `dist/bin/` 中有对应版本的 `wavesrv.arm64`、`wsh-0.14.16-darwin.arm64` 时，可直接启动。缺少二进制时按桌面 BUILD 说明准备运行产物，检查生成命令不要改动任何被跟踪文件；本次只生成了运行所需的忽略产物，没有执行生成绑定、安装依赖或格式化。
3. 从 `apps/desktop` 运行（把 `DEMO_DIR` 设为步骤 1 的输出）：

   ```sh
   WADDLE_HOME="$DEMO_DIR/legacy" \
   WADDLE_CONFIG_HOME="$DEMO_DIR/config" \
   WADDLE_DATA_HOME="$DEMO_DIR/data" \
   WADDLE_NOCONFIRMQUIT=1 \
   WCLOUD_ENDPOINT=https://127.0.0.1:9 \
   WCLOUD_PING_ENDPOINT=https://127.0.0.1:9 \
   WCLOUD_WS_ENDPOINT=wss://127.0.0.1:9 \
   npm run dev -- -- --user-data-dir="$DEMO_DIR/electron" --remote-debugging-port=9225 --remote-debugging-address=127.0.0.1
   ```

4. 保持遥测关闭，跳过欢迎引导。新建标签，关闭默认首页中的私人 home 文件浏览器及在线浏览器区块。只保留一个应用窗口。**进入新终端后显式 `cd` 到步骤 1 的 `demo` 子目录**，设置 `export PS1='demo $ '`，清屏，只运行该目录的 `ls`、`cat notes.txt`。用 `wsh view` 打开该目录中生成的 `README.md`，排成左右区块。截图前检查实际工作目录、标题、终端回显，不能依赖默认 cwd。
5. 调整窗口内容区至 1280×760（Retina 原图为 2560×1520）。从官网目录执行 `node scripts/capture-desktop.mjs /tmp/desktop-workspace-new.png`。脚本只连本机 9225 端口，输出原始 PNG，不覆盖已有文件。
6. 要拍 AI 图，在另一个终端从官网目录运行 `node scripts/desktop-demo.mjs serve`，在 App 中按 `Cmd+Shift+A` 打开面板，确认选中 `Local model`，输入 `Check the demo workspace using the local test connection.`。看到完整演示响应后，用捕获脚本另存 PNG。不要把模型设置页里的兼容协议名拍入国内截图。
7. 逐张目视审查后复制原图到 `assets/screenshots/`，更新 `assets/screenshots.json` 的日期、版本、来源、尺寸和 SHA-256。桌面外观变更时两张图一起重拍，再构建两版、跑 `npm test` 并检查两版网页。退出临时桌面、模型服务，关闭调试端口；保留原有应用与已有预览进程不动。

## 功能核对的边界

本次真实桌面运行确认了 Bash 命令、AI 自定义本地地址的流式响应、`term_get_scrollback` 返回演示终端输出、Markdown 预览、编辑并保存演示文件、区块由左右拖为上下排列。现有 `go test ./pkg/jobmanager ./pkg/remote/... ./pkg/wshrpc/wshremote` 最终通过，覆盖持久会话断连/重连流管理和远程文件相关逻辑；没有连接真实外部 SSH 服务器，也没有测试真实付费模型、本地模型推理、Windows 或 Linux 桌面运行。SSH 文案保留辅助程序与持久会话前提，不承诺远端重启后恢复。

安卓沿用需求 02 已运行的截图、61 项设备测试和折叠布局记录作为各功能的运行证据；本轮还在 `Waddle_API_35 / emulator-5554` 打开既有安装，确认本地 Bash、文件入口、快捷键和 AI 命令入口，未替换 APK、未使用真机或真实凭据。具体账号登录、模型生成和折叠布局结果以 02 的记录为准，不把既有模拟器安装冒充最新源码验收。
