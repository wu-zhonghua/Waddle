---
name: delivering-features
description: Use when implementing or changing a Waddle feature that must be delivered to GitHub, especially with local installation, a dirty worktree, or diverged remote history.
---

# Delivering Waddle Features

## Core Contract

Deliver the approved behavior and only the approved scope. Evidence gates every transition from requirement to design, implementation, installation, commit, and push.

## Execution Record

Record acceptance criteria, included paths/hunks, preserved work, verification, local-install requirement, and target ref before editing. Update the record when the user changes scope; never infer mixed-file ownership from filenames alone.

## Workflow

1. Read `AGENTS.md`, `.kilocode/rules/rules.md`, and every matching project skill.
2. Translate the request into scope, acceptance criteria, and a target ref. Resolve material ambiguities.
3. Inspect branch, remotes, divergence, index, worktree, and untracked files. Reconcile every path or hunk with the record.
4. Design the behavior and obtain approval before implementation. Write a concrete plan for multi-step work.
5. For behavior changes, observe the focused test fail as expected, implement the minimum change, then observe it pass.
6. Run focused tests and the repository-wide checks required by affected systems. A required failure blocks commit and push.
7. Install locally only when requested. Use repository tasks, preserve rollback, verify the bundle, and never run direct `go build`.
8. Review the final diff; stage only approved paths or hunks and audit the complete index.
9. Create focused commits, preserve remaining work recoverably, fetch, and integrate without force. Re-run affected verification after integration.
10. Push only verified commits. Confirm the remote SHA, then restore and verify preserved work.

## Waddle Example

A Web placement change shares `frontend/app/store/block-placement.ts` with unrelated Git placement edits. Record the approved Web hunks and preserved Git hunks separately, observe the focused placement and Web tests fail then pass, and stage only the approved hunks. Stop if they cannot be separated safely.

## Quick Reference

| Gate | Commands |
| --- | --- |
| Inspect | `git status --short --branch`; `git diff`; `git diff --cached`; `git ls-files --others --exclude-standard` |
| Compare remote | `git fetch origin`; `git rev-list --left-right --count HEAD...origin/<branch>` |
| Verify | `npx vitest run <focused-tests>`; `npm test -- --run`; `git diff --check` |
| Generate | `task generate` after changing its Go sources; inspect generated diffs |
| Package | `task package -- <platform arguments>` only when requested; do not run direct `go build` |
| Audit index | `git diff --cached --check`; `git diff --cached --name-status`; `git diff --cached` |
| Confirm remote | `git ls-remote --exit-code origin refs/heads/<branch>`; compare the returned SHA with the pushed commit |

## Stop Gates

These red flags require an immediate stop:

- approved and unrelated ownership cannot be separated;
- required verification fails;
- local installation or rollback is unsafe;
- the staged diff contains unapproved work;
- a synchronization conflict is ambiguous; or
- authentication fails or a non-fast-forward push is rejected.

Preserve recoverable state and report the blocker.

## Common Mistakes

| Mistake | Safe replacement |
| --- | --- |
| `git add -A` or `git add .` | Stage explicit paths or selected hunks; audit the index. |
| Writing tests after implementation | Observe the relevant test fail before the minimal implementation. |
| Treating build success as verification | Run focused behavior tests and required repository-wide checks. |
| Force-pushing diverged history | Fetch, preserve work, integrate normally, and stop on unsafe conflicts. |
| Assuming push output proves delivery | Read the remote ref and compare its SHA with the expected commit. |
