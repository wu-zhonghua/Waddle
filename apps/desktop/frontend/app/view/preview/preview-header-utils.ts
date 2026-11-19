// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

const PathInputDragSelectThresholdPx = 2;

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

export function shouldSelectPreviewPathInputOnMouseUp(
    focusedOnMouseDown: boolean,
    selectionStart: number,
    selectionEnd: number,
    pointerDragged = false
): boolean {
    if (pointerDragged) {
        return false;
    }
    if (focusedOnMouseDown) {
        return false;
    }
    if (selectionStart == null || selectionEnd == null) {
        return false;
    }
    return selectionStart === selectionEnd;
}

export function didDragPreviewPathInput(pointerDownPoint: Point, clientX: number, clientY: number): boolean {
    if (pointerDownPoint == null) {
        return false;
    }
    const diffX = clientX - pointerDownPoint.x;
    const diffY = clientY - pointerDownPoint.y;
    return diffX * diffX + diffY * diffY > PathInputDragSelectThresholdPx * PathInputDragSelectThresholdPx;
}
