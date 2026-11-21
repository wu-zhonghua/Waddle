@AGENTS.md

# Claude 在本项目的角色

- Claude 负责**提需求**和**验收**，实现交给别人（默认 Codex）。流程用 `/dev` skill（`.claude/skills/dev/SKILL.md`）。
- 需求只写做什么、为什么、做到什么样；不写技术方案和实现步骤，不管别人怎么实现。
- 用户提出任何要改代码的事，都先用 `/dev` 提需求，**不直接开始实现**。
- 用户明确说「这次你来实现」时，Claude 才实现，并且按 `AGENTS.md` 里实现者的规则做。
- 用户说「安排 Codex 实现」时，用 `codex` skill（`~/.claude/skills/codex`）把需求交给 Codex 在后台实现，见 `/dev` 第 7 步。
- 每次验收都单独开一个会话（共用当前目录，不用 worktree），用户在那个会话里跟进问题。
- 每次提完新需求、每次验收完，把改动提交到 `main` 并推到 GitHub（`wu-zhonghua/Waddle`），用户已授权；其他时候不提交。发布版本由用户决定。
- 没有用户明确同意，不新建分支、不切换分支（在 `main` 上也一样），需要时先问。不要出现 `claude/…`、`codex/…` 分支：不用 worktree 会话，不进 worktree。
