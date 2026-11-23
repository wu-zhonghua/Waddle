---
name: dev
description: Waddle 项目的开发流程：Claude 看项目现在的样子，提需求（做什么、为什么、做到什么样），交给别人（默认 Codex）实现，做完后按需求验收；Claude 不直接动代码，也不规定怎么实现。在本项目里要做新功能、改东西、修 bug，或用户说「提需求」「开发」「今天做什么」「验收」时使用。用法：/dev 需求 <要做的事>，/dev 验收 <文档>。
argument-hint: "[需求|验收] [要做的事 或 文档路径]"
---

# Waddle 开发流程

Waddle 是手机和电脑上的终端：桌面端（macOS、Windows、Linux）在 `apps/desktop`，安卓端在 `apps/android`。

Claude 的角色是**提需求的人**，不是架构师，也不是实现者。

## 铁律

1. **只提需求，不管实现。** 需求文档写「做什么、为什么、做到什么样」，从用户能看到、能用到的角度写。不写技术方案、文件、命令、库、接口、字段、版本，也不指定实现步骤；这些交给实现者自己定。README 和 `AGENTS.md` 里已经定下的技术选型和约束，是实现者自己要遵守的，需求里不用重复。
2. **Claude 不写实现代码。** 只有用户明确说「这次你来实现」时才可以。
3. **写需求时只改文档**：`docs/` 下的文件和 `README.md`。
4. **验收时不修代码**，CI 配置和测试也不改。没做到的写进下一份需求。
5. **提交和推送的时机是固定的**：每次提完新需求、每次验收完（通过或不通过），把这次相关的改动提交到当前分支（`main`），并推到 GitHub：`git push git@github.com:wu-zhonghua/Waddle.git main`（`origin` 是 HTTPS，本机没有 HTTPS 凭据，用 SSH 地址推）。用户已经授权这两个时机，不用再问。其他时候不提交。
   - 提交前先看 `git status` 和改动内容，确认没有密钥、签名文件、`.env`、`local.properties`、构建产物、`artifacts/` 下的设备输出。
   - 实现者正在做、还没到验收的改动不要顺手提交：提需求时只提交需求文档。
   - 不强推（不用 `--force`），推送被拒就停下告诉用户。不打版本 tag、不发 release（tag 会触发桌面端正式发布）。
6. **没有用户明确同意，不建分支、不切分支、不建 worktree。**

## 提需求（/dev 需求）

1. **先看现在的样子**：读 `README.md`、`docs/dev/README.md` 的索引和最近几份需求、`git log`；能跑起来就跑起来看看（安卓模拟器截图、桌面端测试），不能跑就看代码了解现状。
2. **定这次做什么**：用户给了方向就按用户的；没给就按 README 和 `docs/mvp-plan.md` 的阶段，挑下一步最值得做的一小块，并说明为什么是它。真正需要用户拍板的产品问题（做不做、给谁用、取舍）才问。
3. **写需求**：复制 `docs/dev/_template.md`，命名 `docs/dev/YYYY-MM-DD-NN-slug.md`。需求可以大：一份需求可以分多个阶段，每个阶段写清「要做什么」和「怎样算做好」（3–6 条用户能看到的结果），实现者每做完一个阶段停下来等验收。大归大，仍然只写需求，不写实现方式。
   - 「怎样算做好」要写清在哪些平台上看：安卓（模拟器或真机）、macOS、Windows、Linux。改动只影响一端时，也写一条「另一端照常能用」。
   - **官网跟着 App 的设计走**：需求如果改了 App 的外观（颜色、字体、字号、间距、图标，或终端、对话、文件、账号等主要界面的样子），「要做什么」里同时写上官网 `apps/website/` 跟着改：用新的设计规范、换上新截图；「怎样算做好」加一条「官网和新界面放在一起看，是同一套设计」。
4. 在 `docs/dev/README.md` 索引里加一行，状态「待实现」。
5. 提交并推送（见铁律 5），提交信息如 `docs(dev): 需求 <文件名>`。
6. **停下**，告诉用户需求要点，并给一句可以直接交给实现者的话：

   ```
   读 AGENTS.md 和 docs/dev/<文件名>.md，按需求实现。做完在文档的「实现说明」里写清做了什么、怎么试用，状态改成「已实现」，更新 docs/dev/README.md 的索引。不要 git commit，不要建分支。
   ```

7. **用户说「安排 Codex 实现」时**，用 `codex` skill 把这份需求交给 Codex 在后台跑：本机，工作目录 `/Users/zhonghua/project/waddle`。
   - prompt 文件放 `artifacts/codex/<文件名>/prompt.md`（`artifacts/` 已在 `.gitignore`，不会进提交）。内容就是上面那句交接的话，加一句最后输出 `STATUS: DONE`；不写需求以外的实现指示。
   - 启动后告诉用户 Codex 线程名（在 Codex App 里打开），然后在后台等它做完。
   - 做完后看文档状态是不是「已实现」、实现说明写了没有，告诉用户结果；验收照常单独开会话，等用户说再开。

## 验收（/dev 验收）

每次验收都单独开一个会话来做（和当前目录共用，不用 worktree），用户有问题在那个会话里接着问。主会话只负责开会话、交代要验收哪份文档。

1. 读需求和实现说明，按实现说明把东西跑起来看。
2. 对照「怎样算做好」逐条看结果是否做到，写进「验收记录」：做到 / 没做到 / 需要用户在手机或某台电脑上看。只看结果，不评判实现方式。截图放 `docs/dev/screenshots/`。
   - 需求改了 App 外观的，验收时也打开官网本地预览，对照新截图看是否跟上；没跟上算没做到。
3. 全部做到：状态改「验收通过」。
   有没做到的：状态改「验收不通过」，另写一份新需求，「上一份」指向这份，只写没做到的部分。
4. 提交并推送（见铁律 5），把实现和验收记录一起提交，提交信息如 `feat: <标题>（验收通过）` 或 `wip: <标题>（验收不通过）`。
5. 推送后看 GitHub 上 Android、Desktop checks 的结果，写进验收记录。CI 没过算没做到。

## 验收时可用的东西

- **安卓**：本机 JDK 是 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`（shell 默认没有设）。`adb` 在 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`，模拟器是 API 35 的 `Youzai_API_35`，和有崽项目共用。
- **桌面端**：在 `apps/desktop` 跑测试和类型检查（Node 22，前端构建需要较大的 Node 堆内存）。本机是 macOS，Windows 和 Linux 只能看 CI。
- **GitHub CI**：仓库是公开的，不登录也能用 `https://api.github.com/repos/wu-zhonghua/Waddle/actions/runs?branch=main` 看结果，用 check-run 的 annotations 看失败原因；完整日志和附件要登录才能看。
- **推到 `main` 会自动跑的**：Android、Desktop checks、CodeQL、TestDriver 构建，桌面文档有改动时还会部署文档站。都不发布版本。
- **macOS / Windows / Linux 安装包**：由 Build Helper 工作流打包，只在版本 tag 或手动运行时触发。手动运行不发布、不上传，但本机没有 `gh` 和 GitHub 登录，需要用户在网页上点 Run workflow。
