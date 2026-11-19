# Waddle workspace

- `apps/android` is the Android terminal. Build and test it with its Gradle wrapper.
- `apps/desktop` is the original desktop app. Follow its own `AGENTS.md` and `.kilocode` guides when changing desktop code.
- `.github` contains repository workflows; action paths are relative to the repository root, even when run steps use a different working directory.
- Keep the two applications independently buildable. Shared protocols belong in `protocols` only when both clients consume them.
- Preserve upstream attribution in vendored Android terminal code. Record changes in its provenance document.
- Never commit local SDK paths, credentials, signing keys, generated builds, or device output containing secrets.
- Do not publish a release or push changes unless requested.

## 开发流程

这个项目的每次改动都先有一份需求，再实现，最后验收。需求都在 `docs/dev/`，流程说明见 `docs/dev/README.md`。

一般分工：Claude 提需求和验收，Codex 实现。下面是**实现者**的规则。

1. **开始前先读**：`README.md`、`docs/dev/README.md`，以及这次的需求文档；改桌面端时还要读 `apps/desktop/AGENTS.md`。
2. **怎么实现由你决定**：需求只说做什么、做到什么样；技术方案、代码结构、依赖由你选，但要符合本文件和 README 里已定的约束。
3. **不改需求**：需求部分（现在的样子、要做什么、不做、怎样算做好）不改。需求有问题或做不到，写进「实现说明」，不要自己改需求。
4. **写实现说明**：做了什么、怎么跑起来试用、在哪些平台上试过、有什么没做完或需要知道的。然后把状态改成「已实现」，更新 `docs/dev/README.md` 索引里的状态。
5. **不提交**：不 `git commit`、不 `git push`、不打 tag，等验收通过后由验收者提交。
6. **不建分支、不切分支**：在当前分支、当前目录里工作。没有用户明确同意，不新建分支、不切换分支，也不新建 worktree。不要建 `codex/…`、`claude/…` 这类分支。
