# Delivering Waddle Features Project Skill Design

## Goal

Add a repository-level skill that guides a Waddle feature from requirement clarification through scoped implementation, verification, optional local installation, commit, and GitHub push.

## Scope

The skill applies when a user asks to add, change, or fix a Waddle feature and expects the result to be delivered to GitHub. It coordinates existing project rules and domain skills instead of duplicating their detailed instructions.

The skill does not automate destructive Git operations, stage the entire worktree, force-push, publish releases, or install an application unless the user requested those actions.

## Structure

Create one self-contained skill at `.kilocode/skills/delivering-features/SKILL.md` and register it in the Skill Guides table in `AGENTS.md`. Do not add scripts or reference files: the workflow depends on repository state and user intent, so procedural judgment is safer than mechanical automation.

## Workflow Contract

The skill requires this sequence:

1. Read `AGENTS.md`, `.kilocode/rules/rules.md`, and every matching project skill before acting.
2. Translate the request into explicit scope, acceptance criteria, and push target. Resolve only ambiguities that would materially change the result.
3. Inspect the branch, remote divergence, staged state, unstaged state, and untracked files. Record which paths belong to the feature and which belong to the user.
4. Design the behavior and obtain approval before implementation. For a multi-step change, write a concrete implementation plan.
5. Use test-driven development for behavior changes: observe the relevant test fail, implement the minimum change, and observe focused tests pass.
6. Run verification proportional to risk, including the focused tests and the repository-wide checks needed by affected systems. A required failure blocks commit and push.
7. Build and install locally only when requested. Use the repository task commands, preserve a rollback copy, verify the installed bundle, and never run prohibited direct build commands.
8. Review the final diff and stage only approved paths or hunks. Never use broad staging in a mixed worktree.
9. Synchronize with the remote without force-pushing. Preserve unrelated work before any operation that temporarily cleans the tree, restore it afterward, and re-run affected verification after conflict resolution.
10. Commit and push only the verified scope. Confirm the remote commit matches the pushed local commit before reporting completion.

## Failure Handling

Stop before mutation when the requested scope overlaps user changes and cannot be separated safely. Stop before commit or push when required verification fails, authentication is missing, remote history cannot be integrated safely, or the exact staged diff is not approved. Preserve recoverable state and report the concrete blocker rather than bypassing a gate.

## Validation

Develop the skill with process TDD:

- Run baseline scenarios without the skill for a mixed dirty worktree, a request to skip design/tests, and a failed full test before push.
- Add only guidance needed to close observed gaps.
- Re-run the same scenarios with the skill and verify that agents protect unrelated changes, enforce required gates, and complete remote confirmation.
- Validate the skill metadata and naming with the skill validation tool.

The implementation is complete when the skill is registered, passes validation and forward scenarios, the Web right-sidebar feature still passes its tests, unrelated changes remain intact, and the approved commits are present on `origin/main`.
