# Waddle 官网首页框架

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
- `src/render.mjs`：五个区域的语义结构、线性 SVG 图标、GitHub/许可证地址。下载区是状态文本；唯一主要操作跳到下载区，没有假下载按钮。
- `src/tokens.css`：颜色、系统字体、20/16/14/12 字号、4dp 间距单位、48dp 操作目标、8/12dp 圆角、24dp/1.8dp 图标参数的唯一入口。每组注释标明对应 [Android 设计规范](../../docs/dev/android-design-system.md) 的章节。网页用 `rem` 对应规范数值并尊重浏览器文字缩放。
- `src/styles.css`：布局使用这些变量。840px 对应 App 宽屏导航断点，窄屏按正文、图片的阅读顺序堆叠；479px 以下收紧外边距。规范更新时先改 tokens；若规范改变布局规则，再调整本文件。
- `assets/screenshots/`：从 `docs/dev/screenshots/2026-11-23-02-after-*.png` **原样复制**的真实截图，未裁剪、重绘或改动原文件。`assets/screenshots.json` 逐张记录原始路径和像素尺寸。图片按原比例缩放，点击可查看原图。两种网页语言目前共用中文 App 截图，图注已说明。

更换截图时，把新的真实截图复制到官网目录，更新 `assets/screenshots.json` 的来源与尺寸，以及 `src/content.mjs` 的 `editions` 映射和对应说明。不要改动需求 02 的截图。图片中不要带真实账号、Key 或私人文件。国内版要逐张目视检查：现有国内账号截图中仍写有海外兼容协议名，因此官网改用国内设置页的「账号」入口截图；展开终端截图两版共用，里面没有海外服务内容。

终端示例只演示本地命令，不是在线终端。后续工具明确标为未包含；文案为框架阶段内容。国内版更新图片名单时也要更新 `tests/build.test.mjs` 的已审查名单。

## 验证

`npm test` 校验双语页面、站内链接/锚点、图片尺寸和原图一致性、下载区无操作链接、国内产物无海外服务文字及图片隔离、重建清理与版本互不覆盖。

浏览器验收：在 375px 和 1280px 以上宽度分别检查两版，English → 中文 → English，检查整页文案和图注、下载锚点、无横向溢出、图片原比例、键盘焦点与原图链接。放大文字时也应完整可读。自动构建检查不能代替这一步。

本轮在 macOS / Node.js v25.9.0 执行本地构建和自动检查。侧边浏览器接口返回 `The browser access host is unavailable`，未完成浏览器渲染与交互验收，不把源码检查当作视觉验收结果。未部署或发布。
