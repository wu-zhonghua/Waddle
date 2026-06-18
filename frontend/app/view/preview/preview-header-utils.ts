// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

export function formatPreviewHeaderPath(headerPath: string, fileInfo: FileInfo): string {
    let displayPath = fileInfo?.path ?? headerPath ?? "";
    if (displayPath.startsWith("~") && fileInfo?.dir?.startsWith("/") && fileInfo?.name) {
        displayPath = fileInfo.dir.endsWith("/") ? `${fileInfo.dir}${fileInfo.name}` : `${fileInfo.dir}/${fileInfo.name}`;
    }
    if (displayPath != "/" && displayPath.endsWith("/")) {
        return displayPath.slice(0, -1);
    }
    return displayPath;
}
