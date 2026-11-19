---
name: waveenv
description: Guide for creating PenguEnv narrowings in Pengu. Use when writing a named subset type of PenguEnv for a component tree, documenting environmental dependencies, or enabling mock environments for preview/test server usage.
---

# PenguEnv Narrowing Skill

## Purpose

A PenguEnv narrowing creates a _named subset type_ of `PenguEnv` that:

1. Documents exactly which parts of the environment a component tree actually uses.
2. Forms a type contract so callers and tests know what to provide.
3. Enables mocking in the preview/test server — you only need to implement what's listed.

## When To Create One

Create a narrowing whenever you are writing a component (or group of components) that you want to test in the preview server, or when you want to make the environmental dependencies of a component tree explicit.

## Core Principle: Only Include What You Use

**Only list the fields, methods, atoms, and keys that the component tree actually accesses.** If you don't call `wos`, don't include `wos`. If you only call one RPC command, only list that one command. The narrowing is a precise dependency declaration — not a copy of `PenguEnv`.

## File Location

- **Separate file** (preferred for shared/complex envs): name it `<feature>env.ts` next to the component, e.g. `frontend/app/block/blockenv.ts`.
- **Inline** (acceptable for small, single-file components): export the type directly from the component file, e.g. `WidgetsEnv` in `frontend/app/workspace/widgets.tsx`.

## Imports Required

```ts
import {
  MetaKeyAtomFnType, // only if you use getBlockMetaKeyAtom or getTabMetaKeyAtom
  ConnConfigKeyAtomFnType, // only if you use getConnConfigKeyAtom
  SettingsKeyAtomFnType, // only if you use getSettingsKeyAtom
  PenguEnv,
  PenguEnvSubset,
} from "@/app/waveenv/waveenv";
```

## The Shape

```ts
export type MyEnv = PenguEnvSubset<{
  // --- Simple PenguEnv properties ---
  // Copy the type verbatim from PenguEnv with PenguEnv["key"] syntax.
  isDev: PenguEnv["isDev"];
  createBlock: PenguEnv["createBlock"];
  showContextMenu: PenguEnv["showContextMenu"];
  platform: PenguEnv["platform"];

  // --- electron: list only the methods you call ---
  electron: {
    openExternal: PenguEnv["electron"]["openExternal"];
  };

  // --- rpc: list only the commands you call ---
  rpc: {
    ActivityCommand: PenguEnv["rpc"]["ActivityCommand"];
    ConnEnsureCommand: PenguEnv["rpc"]["ConnEnsureCommand"];
  };

  // --- atoms: list only the atoms you read ---
  atoms: {
    modalOpen: PenguEnv["atoms"]["modalOpen"];
    fullConfigAtom: PenguEnv["atoms"]["fullConfigAtom"];
  };

  // --- wos: always take the whole thing, no sub-typing needed ---
  wos: PenguEnv["wos"];

  // --- services: list only the services you call; no method-level narrowing ---
  services: {
    block: PenguEnv["services"]["block"];
    workspace: PenguEnv["services"]["workspace"];
  };

  // --- key-parameterized atom factories: enumerate the keys you use ---
  getSettingsKeyAtom: SettingsKeyAtomFnType<"app:focusfollowscursor" | "window:magnifiedblockopacity">;
  getBlockMetaKeyAtom: MetaKeyAtomFnType<"view" | "frame:title" | "connection">;
  getTabMetaKeyAtom: MetaKeyAtomFnType<"tabid" | "name">;
  getConnConfigKeyAtom: ConnConfigKeyAtomFnType<"conn:wshenabled">;

  // --- other atom helpers: copy verbatim ---
  getConnStatusAtom: PenguEnv["getConnStatusAtom"];
  getLocalHostDisplayNameAtom: PenguEnv["getLocalHostDisplayNameAtom"];
  getConfigBackgroundAtom: PenguEnv["getConfigBackgroundAtom"];
}>;
```

### Automatically Included Fields

Every `PenguEnvSubset<T>` automatically includes the mock fields — you never need to declare them:

- `isMock: boolean`
- `mockSetPenguObj: <T extends PenguObj>(oref: string, obj: T) => void`
- `mockModels?: Map<any, any>`

### Rules for Each Section

| Section                    | Pattern                                                | Notes                                                                                              |
| -------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| `electron`                 | `electron: { method: PenguEnv["electron"]["method"]; }` | List every method called; omit the rest.                                                           |
| `rpc`                      | `rpc: { Cmd: PenguEnv["rpc"]["Cmd"]; }`                 | List every RPC command called; omit the rest.                                                      |
| `atoms`                    | `atoms: { atom: PenguEnv["atoms"]["atom"]; }`           | List every atom read; omit the rest.                                                               |
| `wos`                      | `wos: PenguEnv["wos"]`                                  | Take the whole `wos` object (no sub-typing needed), but **only add it if `wos` is actually used**. |
| `services`                 | `services: { svc: PenguEnv["services"]["svc"]; }`       | List each service used; take the whole service object (no method-level narrowing).                 |
| `getSettingsKeyAtom`       | `SettingsKeyAtomFnType<"key1" \| "key2">`              | Union all settings keys accessed.                                                                  |
| `getBlockMetaKeyAtom`      | `MetaKeyAtomFnType<"key1" \| "key2">`                  | Union all block meta keys accessed.                                                                |
| `getTabMetaKeyAtom`        | `MetaKeyAtomFnType<"key1" \| "key2">`                  | Union all tab meta keys accessed.                                                                  |
| `getConnConfigKeyAtom`     | `ConnConfigKeyAtomFnType<"key1">`                      | Union all conn config keys accessed.                                                               |
| All other `PenguEnv` fields | `PenguEnv["fieldName"]`                                 | Copy type verbatim.                                                                                |

## Using the Narrowed Type in Components

```ts
import { usePenguEnv } from "@/app/waveenv/waveenv";
import { MyEnv } from "./myenv";

const MyComponent = memo(() => {
    const env = usePenguEnv<MyEnv>();
    // TypeScript now enforces you only access what's in MyEnv.
    const val = useAtomValue(env.getSettingsKeyAtom("app:focusfollowscursor"));
    ...
});
```

The generic parameter on `usePenguEnv<MyEnv>()` casts the context to your narrowed type. The real production `PenguEnv` satisfies every narrowing; mock envs only need to implement the listed subset.

## Real Examples

- `BlockEnv` in `frontend/app/block/blockenv.ts` — complex narrowing with all section types, in a separate file.
- `WidgetsEnv` in `frontend/app/workspace/widgets.tsx` — smaller narrowing defined inline in the component file.
