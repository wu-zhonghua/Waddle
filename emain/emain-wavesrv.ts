// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import * as electron from "electron";
import * as child_process from "node:child_process";
import * as readline from "readline";
import { WebServerEndpointVarName, WSServerEndpointVarName } from "../frontend/util/endpoints";
import { AuthKey, WaddleAuthKeyEnv } from "./authkey";
import { setForceQuit, setUserConfirmedQuit } from "./emain-activity";
import {
    getElectronAppResourcesPath,
    getElectronAppUnpackedBasePath,
    getWaddleConfigDir,
    getWaddleDataDir,
    getWaddleSrvCwd,
    getWaddleSrvPath,
    getXdgCurrentDesktop,
    WaddleConfigHomeVarName,
    WaddleDataHomeVarName,
} from "./emain-platform";
import {
    getElectronExecPath,
    WaddleAppElectronExecPath,
    WaddleAppPathVarName,
    WaddleAppResourcesPathVarName,
} from "./emain-util";
import { updater } from "./updater";

let isWaddleSrvDead = false;
let waveSrvProc: child_process.ChildProcessWithoutNullStreams | null = null;
let WaddleVersion = "unknown"; // set by WAVESRV-ESTART
let WaddleBuildTime = 0; // set by WAVESRV-ESTART

export function getWaddleVersion(): { version: string; buildTime: number } {
    return { version: WaddleVersion, buildTime: WaddleBuildTime };
}

let waveSrvReadyResolve = (value: boolean) => {};
const waveSrvReady: Promise<boolean> = new Promise((resolve, _) => {
    waveSrvReadyResolve = resolve;
});

export function getWaddleSrvReady(): Promise<boolean> {
    return waveSrvReady;
}

export function getWaddleSrvProc(): child_process.ChildProcessWithoutNullStreams | null {
    return waveSrvProc;
}

export function getIsWaddleSrvDead(): boolean {
    return isWaddleSrvDead;
}

export function runWaddleSrv(handleWSEvent: (evtMsg: WSEventType) => void): Promise<boolean> {
    let pResolve: (value: boolean) => void;
    let pReject: (reason?: any) => void;
    const rtnPromise = new Promise<boolean>((argResolve, argReject) => {
        pResolve = argResolve;
        pReject = argReject;
    });
    const envCopy = { ...process.env };
    const xdgCurrentDesktop = getXdgCurrentDesktop();
    if (xdgCurrentDesktop != null) {
        envCopy["XDG_CURRENT_DESKTOP"] = xdgCurrentDesktop;
    }
    envCopy[WaddleAppPathVarName] = getElectronAppUnpackedBasePath();
    envCopy[WaddleAppResourcesPathVarName] = getElectronAppResourcesPath();
    envCopy[WaddleAppElectronExecPath] = getElectronExecPath();
    envCopy[WaddleAuthKeyEnv] = AuthKey;
    envCopy[WaddleDataHomeVarName] = getWaddleDataDir();
    envCopy[WaddleConfigHomeVarName] = getWaddleConfigDir();
    const waveSrvCmd = getWaddleSrvPath();
    console.log("trying to run local server", waveSrvCmd);
    const proc = child_process.spawn(getWaddleSrvPath(), {
        cwd: getWaddleSrvCwd(),
        env: envCopy,
    });
    proc.on("exit", (e) => {
        if (updater?.status == "installing") {
            return;
        }
        console.log("wavesrv exited, shutting down");
        setForceQuit(true);
        isWaddleSrvDead = true;
        electron.app.quit();
    });
    proc.on("spawn", (e) => {
        console.log("spawned wavesrv");
        waveSrvProc = proc;
        pResolve(true);
    });
    proc.on("error", (e) => {
        console.log("error running wavesrv", e);
        pReject(e);
    });
    const rlStdout = readline.createInterface({
        input: proc.stdout,
        terminal: false,
    });
    rlStdout.on("line", (line) => {
        console.log(line);
    });
    const rlStderr = readline.createInterface({
        input: proc.stderr,
        terminal: false,
    });
    rlStderr.on("line", (line) => {
        if (line.includes("WAVESRV-ESTART")) {
            const startParams = /ws:([a-z0-9.:]+) web:([a-z0-9.:]+) version:([a-z0-9.-]+) buildtime:(\d+)/gm.exec(
                line
            );
            if (startParams == null) {
                console.log("error parsing WAVESRV-ESTART line", line);
                setUserConfirmedQuit(true);
                electron.app.quit();
                return;
            }
            process.env[WSServerEndpointVarName] = startParams[1];
            process.env[WebServerEndpointVarName] = startParams[2];
            WaddleVersion = startParams[3];
            WaddleBuildTime = parseInt(startParams[4]);
            waveSrvReadyResolve(true);
            return;
        }
        if (line.startsWith("WAVESRV-EVENT:")) {
            const evtJson = line.slice("WAVESRV-EVENT:".length);
            try {
                const evtMsg: WSEventType = JSON.parse(evtJson);
                handleWSEvent(evtMsg);
            } catch (e) {
                console.log("error handling WAVESRV-EVENT", e);
            }
            return;
        }
        console.log(line);
    });
    return rtnPromise;
}
