// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { ContextMenuModel } from "@/app/store/contextmenu";
import { globalStore } from "@/app/store/jotaiStore";
import { TabRpcClient } from "@/app/store/wshrpcutil";
import {
    TreeView,
    type TreeNodeData,
    type TreeSortDirection,
    type TreeSortField,
    type TreeSortSpec,
} from "@/app/treeview/treeview";
import { FileTransferModel } from "@/app/workspace/file-transfer";
import { useWaddleEnv } from "@/app/waveenv/waveenv";
import { checkKeyPressed, isCharacterKeyEvent } from "@/util/keyutil";
import { PLATFORM, PlatformMacOS } from "@/util/platformutil";
import { addOpenMenuItems } from "@/util/previewutil";
import { fireAndForget } from "@/util/util";
import { formatRemoteUri } from "@/util/waveutil";
import { offset, useDismiss, useFloating, useInteractions } from "@floating-ui/react";
import {
    Header,
    Row,
    RowData,
    Table,
    createColumnHelper,
    flexRender,
    getCoreRowModel,
    getSortedRowModel,
    useReactTable,
} from "@tanstack/react-table";
import clsx from "clsx";
import { PrimitiveAtom, atom, useAtom, useAtomValue, useSetAtom } from "jotai";
import { OverlayScrollbarsComponent, OverlayScrollbarsComponentRef } from "overlayscrollbars-react";
import React, { Fragment, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useDrag, useDrop } from "react-dnd";
import { quote as shellQuote } from "shell-quote";
import { debounce } from "throttle-debounce";
import "./directorypreview.scss";
import { EntryManagerOverlay, EntryManagerOverlayProps, EntryManagerType } from "./entry-manager";
import {
    type DirectoryTreeColumnResizeBounds,
    type DirectoryTreeNodeVisuals,
    fileInfoEntriesToTreeNodes,
    fileInfoToTreeNode,
    filterDirectoryTreeEntries,
    getDirectoryTreeSymlinkVisuals,
    getResizedDirectoryTreeColumnWidth,
} from "./preview-directory-tree";
import {
    cleanMimetype,
    getBestUnit,
    getLastModifiedTime,
    getSortIcon,
    handleFileDelete,
    handleRename,
    isIconValid,
    makeDirectoryDefaultMenuItems,
    mergeError,
    openDirectoryEntry,
    overwriteError,
} from "./preview-directory-utils";
import { type PreviewModel } from "./preview-model";
import type { PreviewEnv } from "./previewenv";

const DirectoryTreeMaxEntries = 500;

const DirectoryTreeSortColumns: { field: TreeSortField; label: string }[] = [
    { field: "modified", label: "Modified" },
    { field: "size", label: "Size" },
];

type DirectoryTreeResizableColumn = "name" | "modified" | "size";
type DirectoryTreeResizeHandleColumn = DirectoryTreeResizableColumn;
type DirectoryTreeColumnWidths = Partial<Record<DirectoryTreeResizableColumn, number>>;

const DirectoryTreeColumnResizeBoundsByColumn: Record<DirectoryTreeResizableColumn, DirectoryTreeColumnResizeBounds> = {
    name: { min: 120, max: 1600 },
    modified: { min: 92, max: 600 },
    size: { min: 48, max: 240 },
};

function hasNativeFiles(dataTransfer: DataTransfer): boolean {
    return Array.from(dataTransfer?.types ?? []).includes("Files");
}

function normalizeTreeIconName(icon: string): string {
    if (!isIconValid(icon)) {
        return null;
    }
    const iconParts = icon.trim().split(/\s+/);
    const iconName = iconParts.find((part) => !part.startsWith("fa-"));
    if (iconName == null) {
        return null;
    }
    if (iconParts.includes("fa-brands")) {
        return `brands@${iconName}`;
    }
    if (iconParts.includes("fa-regular")) {
        return `regular@${iconName}`;
    }
    return iconName;
}

function getFallbackTreeIconColor(fileInfo: FileInfo): string {
    const mimeType = fileInfo.isdir ? "directory" : (fileInfo.mimetype ?? "");
    if (mimeType === "directory") {
        return "var(--term-bright-blue)";
    }
    if (mimeType.startsWith("image/")) {
        return "var(--term-bright-green)";
    }
    if (mimeType.startsWith("video/")) {
        return "var(--term-bright-magenta)";
    }
    if (mimeType.startsWith("audio/")) {
        return "var(--term-bright-cyan)";
    }
    if (mimeType === "application/pdf") {
        return "var(--term-bright-red)";
    }
    if (mimeType.includes("json") || mimeType.includes("yaml") || mimeType.includes("toml")) {
        return "var(--term-yellow)";
    }
    if (mimeType.startsWith("text/markdown") || mimeType.startsWith("text/mdx")) {
        return "var(--term-bright-cyan)";
    }
    if (mimeType.startsWith("text/") || mimeType.includes("javascript") || mimeType.includes("typescript")) {
        return "var(--term-green)";
    }
    return "var(--term-gray)";
}

function getDirectoryTreeNodeVisuals(fileInfo: FileInfo, fullConfig: FullConfigType): DirectoryTreeNodeVisuals {
    const symlinkVisuals = getDirectoryTreeSymlinkVisuals(fileInfo);
    if (symlinkVisuals != null) {
        return symlinkVisuals;
    }
    const mimeType = fileInfo.isdir ? "directory" : (fileInfo.mimetype ?? "");
    let icon: string = null;
    let iconColor: string = null;
    let configKey = mimeType;

    while (configKey.length > 0 && (icon == null || iconColor == null)) {
        const mimeConfig = fullConfig.mimetypes?.[configKey];
        if (icon == null && isIconValid(mimeConfig?.icon)) {
            icon = normalizeTreeIconName(mimeConfig.icon);
        }
        if (iconColor == null && mimeConfig?.color) {
            iconColor = mimeConfig.color;
        }
        configKey = configKey.slice(0, -1);
    }

    return {
        icon,
        iconColor: iconColor ?? getFallbackTreeIconColor(fileInfo),
    };
}

interface DirectoryTableHeaderCellProps {
    header: Header<FileInfo, unknown>;
}

function DirectoryTableHeaderCell({ header }: DirectoryTableHeaderCellProps) {
    return (
        <div
            className="dir-table-head-cell"
            key={header.id}
            style={{ width: `calc(var(--header-${header.id}-size) * 1px)` }}
        >
            <div className="dir-table-head-cell-content" onClick={() => header.column.toggleSorting()}>
                {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                {getSortIcon(header.column.getIsSorted())}
            </div>
            <div className="dir-table-head-resize-box">
                <div
                    className="dir-table-head-resize"
                    onMouseDown={header.getResizeHandler()}
                    onTouchStart={header.getResizeHandler()}
                />
            </div>
        </div>
    );
}

declare module "@tanstack/react-table" {
    interface TableMeta<TData extends RowData> {
        updateName: (path: string, isDir: boolean) => void;
        newFile: () => void;
        newDirectory: () => void;
    }
}

interface DirectoryTableProps {
    model: PreviewModel;
    data: FileInfo[];
    search: string;
    focusIndex: number;
    setFocusIndex: (_: number) => void;
    setSearch: (_: string) => void;
    setSelectedPath: (_: string, isDir?: boolean) => void;
    setRefreshVersion: React.Dispatch<React.SetStateAction<number>>;
    entryManagerOverlayPropsAtom: PrimitiveAtom<EntryManagerOverlayProps>;
    newFile: () => void;
    newDirectory: () => void;
}

const columnHelper = createColumnHelper<FileInfo>();

function DirectoryTable({
    model,
    data,
    search,
    focusIndex,
    setFocusIndex,
    setSearch,
    setSelectedPath,
    setRefreshVersion,
    entryManagerOverlayPropsAtom,
    newFile,
    newDirectory,
}: DirectoryTableProps) {
    const env = useWaddleEnv<PreviewEnv>();
    const fullConfig = useAtomValue(env.atoms.fullConfigAtom);
    const defaultSort = useAtomValue(env.getSettingsKeyAtom("preview:defaultsort")) ?? "name";
    const setErrorMsg = useSetAtom(model.errorMsgAtom);
    const getIconFromMimeType = useCallback(
        (mimeType: string): string => {
            while (mimeType.length > 0) {
                const icon = fullConfig.mimetypes?.[mimeType]?.icon ?? null;
                if (isIconValid(icon)) {
                    return `fa fa-solid fa-${icon} fa-fw`;
                }
                mimeType = mimeType.slice(0, -1);
            }
            return "fa fa-solid fa-file fa-fw";
        },
        [fullConfig.mimetypes]
    );
    const getIconColor = useCallback(
        (mimeType: string): string => fullConfig.mimetypes?.[mimeType]?.color ?? "inherit",
        [fullConfig.mimetypes]
    );
    const columns = useMemo(
        () => [
            columnHelper.accessor("mimetype", {
                cell: (info) => (
                    <i
                        className={getIconFromMimeType(info.getValue() ?? "")}
                        style={{ color: getIconColor(info.getValue() ?? "") }}
                    ></i>
                ),
                header: () => <span></span>,
                id: "logo",
                size: 25,
                enableSorting: false,
            }),
            columnHelper.accessor("name", {
                cell: (info) => <span className="dir-table-name ellipsis">{info.getValue()}</span>,
                header: () => <span className="dir-table-head-name">Name</span>,
                sortingFn: "alphanumeric",
                size: 200,
                minSize: 90,
            }),
            columnHelper.accessor("modestr", {
                cell: (info) => <span className="dir-table-modestr">{info.getValue()}</span>,
                header: () => <span>Perm</span>,
                size: 91,
                minSize: 90,
                sortingFn: "alphanumeric",
            }),
            columnHelper.accessor("modtime", {
                cell: (info) => <span className="dir-table-lastmod">{getLastModifiedTime(info.getValue())}</span>,
                header: () => <span>Last Modified</span>,
                size: 91,
                minSize: 65,
                sortingFn: "datetime",
            }),
            columnHelper.accessor("size", {
                cell: (info) => <span className="dir-table-size">{getBestUnit(info.getValue())}</span>,
                header: () => <span className="dir-table-head-size">Size</span>,
                size: 55,
                minSize: 50,
                sortingFn: "auto",
            }),
            columnHelper.accessor("mimetype", {
                cell: (info) => <span className="dir-table-type ellipsis">{cleanMimetype(info.getValue() ?? "")}</span>,
                header: () => <span className="dir-table-head-type">Type</span>,
                size: 97,
                minSize: 97,
                sortingFn: "alphanumeric",
            }),
            columnHelper.accessor("path", {}),
        ],
        [fullConfig]
    );

    const setEntryManagerProps = useSetAtom(entryManagerOverlayPropsAtom);

    const updateName = useCallback(
        (path: string, isDir: boolean) => {
            const fileName = path.split("/").at(-1);
            setEntryManagerProps({
                entryManagerType: EntryManagerType.EditName,
                startingValue: fileName,
                onSave: (newName: string) => {
                    let newPath: string;
                    if (newName !== fileName) {
                        const lastInstance = path.lastIndexOf(fileName);
                        newPath = path.substring(0, lastInstance) + newName;
                        console.log(`replacing ${fileName} with ${newName}: ${path}`);
                        handleRename(model, path, newPath, isDir, setErrorMsg);
                    }
                    setEntryManagerProps(undefined);
                },
            });
        },
        [model, setErrorMsg]
    );

    const initialSorting = defaultSort === "modtime" ? [{ id: "modtime", desc: true }] : [{ id: "name", desc: false }];

    const table = useReactTable({
        data,
        columns,
        columnResizeMode: "onChange",
        getSortedRowModel: getSortedRowModel(),
        getCoreRowModel: getCoreRowModel(),

        initialState: {
            sorting: initialSorting,
            columnVisibility: {
                path: false,
            },
        },
        enableMultiSort: false,
        enableSortingRemoval: false,
        meta: {
            updateName,
            newFile,
            newDirectory,
        },
    });
    const sortingState = table.getState().sorting;
    useEffect(() => {
        const allRows = table.getRowModel()?.flatRows || [];
        const focusedRow = allRows[focusIndex];
        setSelectedPath((focusedRow?.getValue("path") as string) ?? null, focusedRow?.original?.isdir ?? false);
    }, [focusIndex, data, setSelectedPath, sortingState]);

    const columnSizeVars = useMemo(() => {
        const headers = table.getFlatHeaders();
        const colSizes: { [key: string]: number } = {};
        for (let i = 0; i < headers.length; i++) {
            const header = headers[i]!;
            colSizes[`--header-${header.id}-size`] = header.getSize();
            colSizes[`--col-${header.column.id}-size`] = header.column.getSize();
        }
        return colSizes;
    }, [table.getState().columnSizingInfo]);

    const osRef = useRef<OverlayScrollbarsComponentRef>(null);
    const bodyRef = useRef<HTMLDivElement>(null);
    const [scrollHeight, setScrollHeight] = useState(0);

    const onScroll = useCallback(
        debounce(2, () => {
            setScrollHeight(osRef.current.osInstance().elements().viewport.scrollTop);
        }),
        []
    );

    const TableComponent = table.getState().columnSizingInfo.isResizingColumn ? MemoizedTableBody : TableBody;

    return (
        <OverlayScrollbarsComponent
            options={{ scrollbars: { autoHide: "leave" } }}
            events={{ scroll: onScroll }}
            className="dir-table"
            style={{ ...columnSizeVars }}
            ref={osRef}
            data-scroll-height={scrollHeight}
        >
            <div className="dir-table-head">
                {table.getHeaderGroups().map((headerGroup) => (
                    <div className="dir-table-head-row" key={headerGroup.id}>
                        {headerGroup.headers.map((header) => (
                            <DirectoryTableHeaderCell key={header.id} header={header} />
                        ))}
                    </div>
                ))}
            </div>
            <TableComponent
                bodyRef={bodyRef}
                model={model}
                data={data}
                table={table}
                search={search}
                focusIndex={focusIndex}
                setFocusIndex={setFocusIndex}
                setSearch={setSearch}
                setSelectedPath={setSelectedPath}
                setRefreshVersion={setRefreshVersion}
                osRef={osRef.current}
            />
        </OverlayScrollbarsComponent>
    );
}

interface TableBodyProps {
    bodyRef: React.RefObject<HTMLDivElement>;
    model: PreviewModel;
    data: Array<FileInfo>;
    table: Table<FileInfo>;
    search: string;
    focusIndex: number;
    setFocusIndex: (_: number) => void;
    setSearch: (_: string) => void;
    setSelectedPath: (_: string, isDir?: boolean) => void;
    setRefreshVersion: React.Dispatch<React.SetStateAction<number>>;
    osRef: OverlayScrollbarsComponentRef;
}

function TableBody({
    bodyRef,
    model,
    table,
    search,
    focusIndex,
    setFocusIndex,
    setSearch,
    setRefreshVersion,
    osRef,
}: TableBodyProps) {
    const searchActive = useAtomValue(model.directorySearchActive);
    const dummyLineRef = useRef<HTMLDivElement>(null);
    const warningBoxRef = useRef<HTMLDivElement>(null);
    const conn = useAtomValue(model.connection);
    const setErrorMsg = useSetAtom(model.errorMsgAtom);

    useEffect(() => {
        if (focusIndex === null || !bodyRef.current || !osRef) {
            return;
        }

        const rowElement = bodyRef.current.querySelector(`[data-rowindex="${focusIndex}"]`) as HTMLDivElement;
        if (!rowElement) {
            return;
        }

        const viewport = osRef.osInstance().elements().viewport;
        const viewportHeight = viewport.offsetHeight;
        const rowRect = rowElement.getBoundingClientRect();
        const parentRect = viewport.getBoundingClientRect();
        const viewportScrollTop = viewport.scrollTop;
        const rowTopRelativeToViewport = rowRect.top - parentRect.top + viewport.scrollTop;
        const rowBottomRelativeToViewport = rowRect.bottom - parentRect.top + viewport.scrollTop;

        if (rowTopRelativeToViewport - 30 < viewportScrollTop) {
            // Row is above the visible area
            let topVal = rowTopRelativeToViewport - 30;
            if (topVal < 0) {
                topVal = 0;
            }
            viewport.scrollTo({ top: topVal });
        } else if (rowBottomRelativeToViewport + 5 > viewportScrollTop + viewportHeight) {
            // Row is below the visible area
            const topVal = rowBottomRelativeToViewport - viewportHeight + 5;
            viewport.scrollTo({ top: topVal });
        }
    }, [focusIndex]);

    const handleFileContextMenu = useCallback(
        async (e: any, finfo: FileInfo) => {
            e.preventDefault();
            e.stopPropagation();
            if (finfo == null) {
                return;
            }
            const fileName = finfo.path.split("/").pop();
            const menu: ContextMenuItem[] = [
                {
                    label: "New File",
                    click: () => {
                        table.options.meta.newFile();
                    },
                },
                {
                    label: "New Folder",
                    click: () => {
                        table.options.meta.newDirectory();
                    },
                },
                {
                    label: "Rename",
                    click: () => {
                        table.options.meta.updateName(finfo.path, finfo.isdir);
                    },
                },
                {
                    type: "separator",
                },
                {
                    label: "Copy File Name",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(fileName)),
                },
                {
                    label: "Copy Full File Name",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(finfo.path)),
                },
                {
                    label: "Copy File Name (Shell Quoted)",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(shellQuote([fileName]))),
                },
                {
                    label: "Copy Full File Name (Shell Quoted)",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(shellQuote([finfo.path]))),
                },
            ];
            addOpenMenuItems(menu, conn, finfo);
            menu.push(
                {
                    type: "separator",
                },
                {
                    label: "Default Settings",
                    submenu: makeDirectoryDefaultMenuItems(model),
                },
                {
                    type: "separator",
                },
                {
                    label: "Delete",
                    click: () => handleFileDelete(model, finfo.path, false, setErrorMsg),
                }
            );
            ContextMenuModel.getInstance().showContextMenu(menu, e);
        },
        [setRefreshVersion, conn]
    );

    const allRows = table.getRowModel().flatRows;
    const dotdotRow = allRows.find((row) => row.getValue("name") === "..");
    const otherRows = allRows.filter((row) => row.getValue("name") !== "..");

    return (
        <div className="dir-table-body" ref={bodyRef}>
            {(searchActive || search !== "") && (
                <div className="flex rounded-[3px] py-1 px-2 bg-warning text-black" ref={warningBoxRef}>
                    <span>{search === "" ? "Type to search (Esc to cancel)" : `Searching for "${search}"`}</span>
                    <div
                        className="ml-auto bg-transparent flex justify-center items-center flex-col p-0.5 rounded-md hover:bg-hoverbg focus:bg-hoverbg focus-within:bg-hoverbg cursor-pointer"
                        onClick={() => {
                            setSearch("");
                            globalStore.set(model.directorySearchActive, false);
                        }}
                    >
                        <i className="fa-solid fa-xmark" />
                        <input
                            type="text"
                            value={search}
                            onChange={() => {}}
                            className="w-0 h-0 opacity-0 p-0 border-none pointer-events-none"
                        />
                    </div>
                </div>
            )}
            <div className="dir-table-body-scroll-box">
                <div className="dummy dir-table-body-row" ref={dummyLineRef}>
                    <div className="dir-table-body-cell">dummy-data</div>
                </div>
                {dotdotRow && (
                    <TableRow
                        model={model}
                        row={dotdotRow}
                        focusIndex={focusIndex}
                        setFocusIndex={setFocusIndex}
                        setSearch={setSearch}
                        idx={0}
                        handleFileContextMenu={handleFileContextMenu}
                        key="dotdot"
                    />
                )}
                {otherRows.map((row, idx) => (
                    <TableRow
                        model={model}
                        row={row}
                        focusIndex={focusIndex}
                        setFocusIndex={setFocusIndex}
                        setSearch={setSearch}
                        idx={dotdotRow ? idx + 1 : idx}
                        handleFileContextMenu={handleFileContextMenu}
                        key={idx}
                    />
                ))}
            </div>
        </div>
    );
}

type TableRowProps = {
    model: PreviewModel;
    row: Row<FileInfo>;
    focusIndex: number;
    setFocusIndex: (_: number) => void;
    setSearch: (_: string) => void;
    idx: number;
    handleFileContextMenu: (e: any, finfo: FileInfo) => Promise<void>;
};

function TableRow({ model, row, focusIndex, setFocusIndex, setSearch, idx, handleFileContextMenu }: TableRowProps) {
    const dirPath = useAtomValue(model.statFilePath);
    const connection = useAtomValue(model.connection);

    const dragItem: DraggedFile = {
        relName: row.getValue("name") as string,
        absParent: dirPath,
        uri: formatRemoteUri(row.getValue("path") as string, connection),
        isDir: row.original.isdir,
    };
    const [_, drag] = useDrag(
        () => ({
            type: "FILE_ITEM",
            canDrag: true,
            item: () => dragItem,
        }),
        [dragItem]
    );

    const dragRef = useCallback(
        (node: HTMLDivElement | null) => {
            drag(node);
        },
        [drag]
    );

    return (
        <div
            className={clsx("dir-table-body-row", { focused: focusIndex === idx })}
            data-rowindex={idx}
            onDoubleClick={() => {
                const newFileName = row.getValue("path") as string;
                fireAndForget(() => openDirectoryEntry(model, newFileName, row.original.isdir, connection));
                setSearch("");
                globalStore.set(model.directorySearchActive, false);
            }}
            onClick={() => setFocusIndex(idx)}
            onContextMenu={(e) => handleFileContextMenu(e, row.original)}
            ref={dragRef}
        >
            {row.getVisibleCells().map((cell) => (
                <div
                    className={clsx("dir-table-body-cell", "col-" + cell.column.id)}
                    key={cell.id}
                    style={{ width: `calc(var(--col-${cell.column.id}-size) * 1px)` }}
                >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </div>
            ))}
        </div>
    );
}

const MemoizedTableBody = React.memo(
    TableBody,
    (prev, next) => prev.table.options.data == next.table.options.data
) as typeof TableBody;

interface DirectoryTreeProps {
    model: PreviewModel;
    data: FileInfo[];
    search: string;
    setSearch: (_: string) => void;
    setSelectedPath: (_: string, isDir?: boolean) => void;
    dirPath: string;
    refreshVersion: number;
    entryManagerOverlayPropsAtom: PrimitiveAtom<EntryManagerOverlayProps>;
    newFile: () => void;
    newDirectory: () => void;
}

function DirectoryTree({
    model,
    data,
    search,
    setSearch,
    setSelectedPath,
    dirPath,
    refreshVersion,
    entryManagerOverlayPropsAtom,
    newFile,
    newDirectory,
}: DirectoryTreeProps) {
    const env = useWaddleEnv<PreviewEnv>();
    const showHiddenFiles = useAtomValue(model.showHiddenFiles);
    const searchActive = useAtomValue(model.directorySearchActive);
    const conn = useAtomValue(model.connection);
    const fullConfig = useAtomValue(env.atoms.fullConfigAtom);
    const defaultSort = useAtomValue(env.getSettingsKeyAtom("preview:defaultsort")) ?? "name";
    const setErrorMsg = useSetAtom(model.errorMsgAtom);
    const setEntryManagerProps = useSetAtom(entryManagerOverlayPropsAtom);
    const [sortSpec, setSortSpec] = useState<TreeSortSpec>(() => getInitialDirectoryTreeSort(defaultSort));
    const [columnWidths, setColumnWidths] = useState<DirectoryTreeColumnWidths>({});
    const panelRef = useRef<HTMLDivElement>(null);
    const transferModel = FileTransferModel.getInstance();

    useEffect(() => {
        setSortSpec(getInitialDirectoryTreeSort(defaultSort));
    }, [defaultSort]);

    const treeData = useMemo(() => {
        const entries = filterDirectoryTreeEntries(data, showHiddenFiles, search);
        return fileInfoEntriesToTreeNodes(entries, dirPath, (entry) => getDirectoryTreeNodeVisuals(entry, fullConfig));
    }, [data, dirPath, fullConfig, search, showHiddenFiles]);

    const selectedPathSeed = treeData.rootIds[0] ?? dirPath ?? "";
    const selectedNodeSeed = treeData.initialNodes[selectedPathSeed];
    useEffect(() => {
        setSelectedPath(selectedPathSeed, selectedNodeSeed?.isDirectory ?? false);
    }, [selectedNodeSeed, selectedPathSeed, setSelectedPath]);

    const fetchDir = useCallback(
        async (id: string, limit: number) => {
            const entries: FileInfo[] = [];
            const remotePath = await model.formatRemoteUri(id, globalStore.get);
            const stream = env.rpc.FileListStreamCommand(TabRpcClient, { path: remotePath }, null);
            for await (const chunk of stream) {
                if (chunk?.fileinfo) {
                    entries.push(...chunk.fileinfo);
                }
            }
            const filteredEntries = filterDirectoryTreeEntries(entries, showHiddenFiles, search);
            const capped = filteredEntries.length > limit;
            const visibleEntries = filteredEntries.slice(0, limit);
            return {
                nodes: visibleEntries.map((entry) =>
                    fileInfoToTreeNode(entry, id, getDirectoryTreeNodeVisuals(entry, fullConfig))
                ),
                capped,
                totalKnown: filteredEntries.length,
            };
        },
        [env, fullConfig, model, search, showHiddenFiles]
    );

    const updateName = useCallback(
        (path: string, isDir: boolean) => {
            const fileName = path.split("/").at(-1);
            setEntryManagerProps({
                entryManagerType: EntryManagerType.EditName,
                startingValue: fileName,
                onSave: (newName: string) => {
                    if (newName !== fileName) {
                        const lastInstance = path.lastIndexOf(fileName);
                        const newPath = path.substring(0, lastInstance) + newName;
                        handleRename(model, path, newPath, isDir, setErrorMsg);
                    }
                    setEntryManagerProps(undefined);
                },
            });
        },
        [model, setEntryManagerProps, setErrorMsg]
    );

    const handleTreeNodeContextMenu = useCallback(
        (e: React.MouseEvent<HTMLDivElement>, _id: string, node: TreeNodeData) => {
            e.preventDefault();
            e.stopPropagation();
            const filePath = node.path ?? node.id;
            const fileName = node.label ?? filePath.split("/").pop() ?? filePath;
            const finfo: FileInfo = {
                name: fileName,
                path: filePath,
                isdir: node.isDirectory,
                size: node.size,
                mimetype: node.mimeType ?? (node.isDirectory ? "directory" : ""),
            };
            const onDownloadFile = (remoteUri: string, downloadInfo: FileInfo) => {
                fireAndForget(async () => {
                    await transferModel.startDownloadFile({
                        rpc: env.rpc,
                        electron: env.electron,
                        sourcePath: remoteUri,
                        fileInfo: downloadInfo,
                        onComplete: model.refreshCallback,
                        onError: (error) =>
                            setErrorMsg({
                                status: "Download Failed",
                                text: error.message,
                                level: "error",
                            }),
                    });
                });
            };
            const menu: ContextMenuItem[] = [
                {
                    label: "New File",
                    click: () => {
                        newFile();
                    },
                },
                {
                    label: "New Folder",
                    click: () => {
                        newDirectory();
                    },
                },
                {
                    label: "Rename",
                    click: () => {
                        updateName(filePath, node.isDirectory);
                    },
                },
                {
                    type: "separator",
                },
                {
                    label: "Copy File Name",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(fileName)),
                },
                {
                    label: "Copy Full File Name",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(filePath)),
                },
                {
                    label: "Copy File Name (Shell Quoted)",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(shellQuote([fileName]))),
                },
                {
                    label: "Copy Full File Name (Shell Quoted)",
                    click: () => fireAndForget(() => navigator.clipboard.writeText(shellQuote([filePath]))),
                },
            ];
            addOpenMenuItems(menu, conn, finfo, { onDownloadFile });
            menu.push(
                {
                    type: "separator",
                },
                {
                    label: "Default Settings",
                    submenu: makeDirectoryDefaultMenuItems(model),
                },
                {
                    type: "separator",
                },
                {
                    label: "Delete",
                    click: () => handleFileDelete(model, filePath, false, setErrorMsg),
                }
            );
            ContextMenuModel.getInstance().showContextMenu(menu, e);
        },
        [conn, env.electron, env.rpc, model, newDirectory, newFile, setErrorMsg, transferModel, updateName]
    );

    const handleSort = useCallback((field: TreeSortField) => {
        setSortSpec((current) => {
            if (current.field === field) {
                return {
                    field,
                    direction: current.direction === "asc" ? "desc" : "asc",
                };
            }
            return {
                field,
                direction: getDefaultDirectoryTreeSortDirection(field),
            };
        });
    }, []);

    const handleColumnResizeStart = useCallback(
        (column: DirectoryTreeResizeHandleColumn, event: React.PointerEvent<HTMLButtonElement>) => {
            const panel = panelRef.current;
            if (panel == null) {
                return;
            }
            const startWidths: Record<DirectoryTreeResizableColumn, number> = {
                name: getDirectoryTreeHeaderColumnWidth(panel, "name"),
                modified: getDirectoryTreeHeaderColumnWidth(panel, "modified"),
                size: getDirectoryTreeHeaderColumnWidth(panel, "size"),
            };
            const startWidth = startWidths[column];
            if (startWidth <= 0) {
                return;
            }
            const startX = event.clientX;

            event.preventDefault();
            event.stopPropagation();

            const handlePointerMove = (moveEvent: PointerEvent) => {
                const width = getResizedDirectoryTreeColumnWidth(
                    startWidth,
                    moveEvent.clientX - startX,
                    DirectoryTreeColumnResizeBoundsByColumn[column]
                );
                setColumnWidths({
                    ...startWidths,
                    [column]: width,
                });
            };
            const stopResize = () => {
                window.removeEventListener("pointermove", handlePointerMove);
                window.removeEventListener("pointerup", stopResize);
                window.removeEventListener("pointercancel", stopResize);
            };

            window.addEventListener("pointermove", handlePointerMove);
            window.addEventListener("pointerup", stopResize);
            window.addEventListener("pointercancel", stopResize);
        },
        []
    );

    const treeColumnStyle = useMemo(() => {
        const style: React.CSSProperties & Record<string, string> = {};
        if (columnWidths.name != null) {
            style["--dir-tree-name-width"] = `${columnWidths.name}px`;
            style["--dir-tree-name-min-width"] = `${columnWidths.name}px`;
        }
        if (columnWidths.modified != null) {
            style["--dir-tree-modified-width"] = `${columnWidths.modified}px`;
            style["--dir-tree-modified-track-width"] = `${columnWidths.modified}px`;
        }
        if (columnWidths.size != null) {
            style["--dir-tree-size-width"] = `${columnWidths.size}px`;
        }
        return style;
    }, [columnWidths]);

    return (
        <div className="dir-tree-panel" ref={panelRef} style={treeColumnStyle}>
            {(searchActive || search !== "") && (
                <div className="flex rounded-[3px] py-1 px-2 bg-warning text-black">
                    <span>{search === "" ? "Type to search (Esc to cancel)" : `Searching for "${search}"`}</span>
                    <div
                        className="ml-auto bg-transparent flex justify-center items-center flex-col p-0.5 rounded-md hover:bg-hoverbg focus:bg-hoverbg focus-within:bg-hoverbg cursor-pointer"
                        onClick={() => {
                            setSearch("");
                            globalStore.set(model.directorySearchActive, false);
                        }}
                    >
                        <i className="fa-solid fa-xmark" />
                    </div>
                </div>
            )}
            <DirectoryTreeHeader sortSpec={sortSpec} onSort={handleSort} onResizeStart={handleColumnResizeStart} />
            <TreeView
                key={`${dirPath}:${search}:${showHiddenFiles}`}
                rootIds={treeData.rootIds}
                initialNodes={treeData.initialNodes}
                fetchDir={fetchDir}
                sortSpec={sortSpec}
                maxDirEntries={DirectoryTreeMaxEntries}
                minWidth={0}
                maxWidth={1000000}
                width="100%"
                height="100%"
                className="dir-tree"
                renderNodeDetails={(node) => <DirectoryTreeNodeDetails node={node} />}
                reloadSignal={refreshVersion}
                onOpenFile={(_id, node) => {
                    fireAndForget(() => openDirectoryEntry(model, node.path ?? node.id, node.isDirectory, conn));
                    setSearch("");
                    globalStore.set(model.directorySearchActive, false);
                }}
                onSelectionChange={(_id, node) => setSelectedPath(node.path ?? node.id, node.isDirectory)}
                onNodeContextMenu={handleTreeNodeContextMenu}
            />
        </div>
    );
}

function getDefaultDirectoryTreeSortDirection(field: TreeSortField): TreeSortDirection {
    if (field === "name") {
        return "asc";
    }
    return "desc";
}

function getInitialDirectoryTreeSort(defaultSort: string): TreeSortSpec {
    if (defaultSort === "modtime") {
        return { field: "modified", direction: "desc" };
    }
    return { field: "name", direction: "asc" };
}

function getDirectoryTreeHeaderColumnWidth(panel: HTMLElement, column: DirectoryTreeResizableColumn): number {
    const headerCell = panel.querySelector<HTMLElement>(`.dir-tree-head-${column}`);
    return Math.round(headerCell?.getBoundingClientRect().width ?? 0);
}

function DirectoryTreeSortButton({
    field,
    label,
    sortSpec,
    onSort,
    resizeColumn,
    onResizeStart,
}: {
    field: TreeSortField;
    label: string;
    sortSpec: TreeSortSpec;
    onSort: (field: TreeSortField) => void;
    resizeColumn?: DirectoryTreeResizeHandleColumn;
    onResizeStart: (column: DirectoryTreeResizeHandleColumn, event: React.PointerEvent<HTMLButtonElement>) => void;
}) {
    const active = sortSpec.field === field;
    return (
        <div className={clsx("dir-tree-head-cell", `dir-tree-head-${field}`, active && "active")}>
            <button
                type="button"
                className="dir-tree-head-sort-button"
                aria-sort={active ? (sortSpec.direction === "asc" ? "ascending" : "descending") : "none"}
                onClick={() => onSort(field)}
            >
                <span>{label}</span>
                {active && (
                    <i
                        className={clsx(
                            "fa-solid dir-tree-head-sort",
                            sortSpec.direction === "asc" ? "fa-chevron-up" : "fa-chevron-down"
                        )}
                    />
                )}
            </button>
            {resizeColumn != null && (
                <button
                    type="button"
                    className="dir-tree-head-resize"
                    aria-label={`Resize ${label} column`}
                    onClick={(event) => event.stopPropagation()}
                    onPointerDown={(event) => onResizeStart(resizeColumn, event)}
                />
            )}
        </div>
    );
}

function DirectoryTreeHeader({
    sortSpec,
    onSort,
    onResizeStart,
}: {
    sortSpec: TreeSortSpec;
    onSort: (field: TreeSortField) => void;
    onResizeStart: (column: DirectoryTreeResizeHandleColumn, event: React.PointerEvent<HTMLButtonElement>) => void;
}) {
    const modifiedColumn = DirectoryTreeSortColumns[0];
    const sizeColumn = DirectoryTreeSortColumns[1];

    return (
        <div className="dir-tree-head">
            <DirectoryTreeSortButton
                field="name"
                label="Name"
                sortSpec={sortSpec}
                onSort={onSort}
                resizeColumn="name"
                onResizeStart={onResizeStart}
            />
            <DirectoryTreeSortButton
                field={modifiedColumn.field}
                label={modifiedColumn.label}
                sortSpec={sortSpec}
                onSort={onSort}
                resizeColumn="modified"
                onResizeStart={onResizeStart}
            />
            <DirectoryTreeSortButton
                field={sizeColumn.field}
                label={sizeColumn.label}
                sortSpec={sortSpec}
                onSort={onSort}
                resizeColumn="size"
                onResizeStart={onResizeStart}
            />
        </div>
    );
}

function formatTreeTime(unixMillis: number): string {
    if (!Number.isFinite(unixMillis) || unixMillis <= 0) {
        return "-";
    }
    return getLastModifiedTime(unixMillis);
}

function DirectoryTreeNodeDetails({ node }: { node: TreeNodeData }) {
    const modified = formatTreeTime(node.modTime);
    const size = getBestUnit(node.size);

    return (
        <>
            <span className="dir-tree-detail dir-tree-detail-modified" title={`Modified: ${modified}`}>
                <span className="dir-tree-detail-value">{modified}</span>
            </span>
            <span className="dir-tree-detail dir-tree-detail-size" title={`Size: ${size}`}>
                <span className="dir-tree-detail-value">{size}</span>
            </span>
        </>
    );
}

interface DirectoryPreviewProps {
    model: PreviewModel;
}

function DirectoryPreview({ model }: DirectoryPreviewProps) {
    const env = useWaddleEnv<PreviewEnv>();
    const [searchText, setSearchText] = useState("");
    const [unfilteredData, setUnfilteredData] = useState<FileInfo[]>([]);
    const [selectedPath, setSelectedPath] = useState("");
    const [selectedPathIsDir, setSelectedPathIsDir] = useState(false);
    const [refreshVersion, setRefreshVersion] = useAtom(model.refreshVersion);
    const conn = useAtomValue(model.connection);
    const blockData = useAtomValue(model.blockAtom);
    const finfo = useAtomValue(model.statFile);
    const dirPath = finfo?.path ?? "";
    const setErrorMsg = useSetAtom(model.errorMsgAtom);
    const transferModel = FileTransferModel.getInstance();
    const [nativeDragActive, setNativeDragActive] = useState(false);

    const setSelectedEntry = useCallback((path: string, isDir = false) => {
        setSelectedPath(path);
        setSelectedPathIsDir(isDir);
    }, []);

    useEffect(() => {
        model.refreshCallback = () => {
            setRefreshVersion((refreshVersion) => refreshVersion + 1);
        };
        return () => {
            model.refreshCallback = null;
        };
    }, [setRefreshVersion]);

    useEffect(
        () =>
            fireAndForget(async () => {
                if (dirPath == "") {
                    setUnfilteredData([]);
                    return;
                }
                const entries: FileInfo[] = [];
                try {
                    const remotePath = await model.formatRemoteUri(dirPath, globalStore.get);
                    const stream = env.rpc.FileListStreamCommand(TabRpcClient, { path: remotePath }, null);
                    for await (const chunk of stream) {
                        if (chunk?.fileinfo) {
                            entries.push(...chunk.fileinfo);
                        }
                    }
                    if (finfo?.dir && finfo?.path !== finfo?.dir) {
                        entries.unshift({
                            name: "..",
                            path: finfo.dir,
                            isdir: true,
                            modtime: new Date().getTime(),
                            mimetype: "directory",
                        });
                    }
                } catch (e) {
                    console.error("Directory Read Error", e);
                    setErrorMsg({
                        status: "Cannot Read Directory",
                        text: `${e}`,
                    });
                }
                setUnfilteredData(entries);
            }),
        [conn, dirPath, refreshVersion]
    );

    useEffect(() => {
        model.directoryKeyDownHandler = (waveEvent: WaddleKeyboardEvent): boolean => {
            if (checkKeyPressed(waveEvent, "Cmd:f")) {
                globalStore.set(model.directorySearchActive, true);
                return true;
            }
            if (checkKeyPressed(waveEvent, "Escape")) {
                setSearchText("");
                globalStore.set(model.directorySearchActive, false);
                return;
            }
            if (checkKeyPressed(waveEvent, "Enter")) {
                if (selectedPath == null || selectedPath == "") {
                    return;
                }
                fireAndForget(() => openDirectoryEntry(model, selectedPath, selectedPathIsDir, conn));
                setSearchText("");
                globalStore.set(model.directorySearchActive, false);
                return true;
            }
            if (checkKeyPressed(waveEvent, "Backspace")) {
                if (searchText.length == 0) {
                    return true;
                }
                setSearchText((current) => current.slice(0, -1));
                return true;
            }
            if (
                checkKeyPressed(waveEvent, "Space") &&
                searchText == "" &&
                PLATFORM == PlatformMacOS &&
                !blockData?.meta?.connection
            ) {
                env.electron.onQuicklook(selectedPath);
                return true;
            }
            if (isCharacterKeyEvent(waveEvent)) {
                setSearchText((current) => current + waveEvent.key);
                return true;
            }
            return false;
        };
        return () => {
            model.directoryKeyDownHandler = null;
        };
    }, [conn, selectedPath, selectedPathIsDir, searchText]);

    const entryManagerPropsAtom = useState(
        atom<EntryManagerOverlayProps>(null) as PrimitiveAtom<EntryManagerOverlayProps>
    )[0];
    const [entryManagerProps, setEntryManagerProps] = useAtom(entryManagerPropsAtom);

    const { refs, floatingStyles, context } = useFloating({
        open: !!entryManagerProps,
        onOpenChange: () => setEntryManagerProps(undefined),
        middleware: [offset(({ rects }) => -rects.reference.height / 2 - rects.floating.height / 2)],
    });

    const handleDropCopy = useCallback(
        async (data: CommandFileCopyData, isDir: boolean) => {
            try {
                await env.rpc.FileCopyCommand(TabRpcClient, data, { timeout: data.opts.timeout });
            } catch (e) {
                console.warn("Copy failed:", e);
                const copyError = `${e}`;
                const allowRetry = copyError.includes(overwriteError) || copyError.includes(mergeError);
                let errorMsg: ErrorMsg;
                if (allowRetry) {
                    errorMsg = {
                        status: "Confirm Overwrite File(s)",
                        text: "This copy operation will overwrite an existing file. Would you like to continue?",
                        level: "warning",
                        buttons: [
                            {
                                text: "Delete Then Copy",
                                onClick: async () => {
                                    data.opts.overwrite = true;
                                    await handleDropCopy(data, isDir);
                                },
                            },
                            {
                                text: "Sync",
                                onClick: async () => {
                                    data.opts.merge = true;
                                    await handleDropCopy(data, isDir);
                                },
                            },
                        ],
                    };
                } else {
                    errorMsg = {
                        status: "Copy Failed",
                        text: copyError,
                        level: "error",
                    };
                }
                setErrorMsg(errorMsg);
            }
            model.refreshCallback();
        },
        [model.refreshCallback]
    );

    const [, drop] = useDrop(
        () => ({
            accept: "FILE_ITEM", //a name of file drop type
            canDrop: (_, monitor) => {
                const dragItem = monitor.getItem<DraggedFile>();
                // drop if not current dir is the parent directory of the dragged item
                // requires absolute path
                if (monitor.isOver({ shallow: false }) && dragItem.absParent !== dirPath) {
                    return true;
                }
                return false;
            },
            drop: async (draggedFile: DraggedFile, monitor) => {
                if (!monitor.didDrop()) {
                    const timeoutYear = 31536000000; // one year
                    const opts: FileCopyOpts = {
                        timeout: timeoutYear,
                    };
                    const desturi = await model.formatRemoteUri(dirPath, globalStore.get);
                    const data: CommandFileCopyData = {
                        srcuri: draggedFile.uri,
                        desturi,
                        opts,
                    };
                    await handleDropCopy(data, draggedFile.isDir);
                }
            },
            // TODO: mabe add a hover option?
        }),
        [dirPath, model.formatRemoteUri, model.refreshCallback]
    );

    useEffect(() => {
        drop(refs.reference);
    }, [refs.reference]);

    const dismiss = useDismiss(context);
    const { getReferenceProps, getFloatingProps } = useInteractions([dismiss]);

    const newFile = useCallback(() => {
        setEntryManagerProps({
            entryManagerType: EntryManagerType.NewFile,
            onSave: (newName: string) => {
                console.log(`newFile: ${newName}`);
                fireAndForget(async () => {
                    await env.rpc.FileCreateCommand(
                        TabRpcClient,
                        {
                            info: {
                                path: await model.formatRemoteUri(`${dirPath}/${newName}`, globalStore.get),
                            },
                        },
                        null
                    );
                    model.refreshCallback();
                });
                setEntryManagerProps(undefined);
            },
        });
    }, [dirPath]);
    const newDirectory = useCallback(() => {
        setEntryManagerProps({
            entryManagerType: EntryManagerType.NewDirectory,
            onSave: (newName: string) => {
                console.log(`newDirectory: ${newName}`);
                fireAndForget(async () => {
                    await env.rpc.FileMkdirCommand(TabRpcClient, {
                        info: {
                            path: await model.formatRemoteUri(`${dirPath}/${newName}`, globalStore.get),
                        },
                    });
                    model.refreshCallback();
                });
                setEntryManagerProps(undefined);
            },
        });
    }, [dirPath]);

    const handleFileContextMenu = useCallback(
        (e: any) => {
            e.preventDefault();
            e.stopPropagation();
            const menu: ContextMenuItem[] = [
                {
                    label: "New File",
                    click: () => {
                        newFile();
                    },
                },
                {
                    label: "New Folder",
                    click: () => {
                        newDirectory();
                    },
                },
                {
                    type: "separator",
                },
            ];
            addOpenMenuItems(menu, conn, finfo, {
                onDownloadFile: (remoteUri, downloadInfo) => {
                    fireAndForget(async () => {
                        await transferModel.startDownloadFile({
                            rpc: env.rpc,
                            electron: env.electron,
                            sourcePath: remoteUri,
                            fileInfo: downloadInfo,
                            onComplete: model.refreshCallback,
                            onError: (error) =>
                                setErrorMsg({
                                    status: "Download Failed",
                                    text: error.message,
                                    level: "error",
                                }),
                        });
                    });
                },
            });

            ContextMenuModel.getInstance().showContextMenu(menu, e);
        },
        [conn, env.electron, env.rpc, finfo, model, newDirectory, newFile, setErrorMsg, transferModel]
    );

    const startUpload = useCallback(
        (file: File) => {
            fireAndForget(async () => {
                await transferModel.startUploadFile({
                    rpc: env.rpc,
                    file,
                    destinationDir: dirPath,
                    formatRemotePath: (path) => model.formatRemoteUri(path, globalStore.get),
                    onComplete: model.refreshCallback,
                    onError: (error) =>
                        setErrorMsg({
                            status: "Upload Failed",
                            text: error.message,
                            level: "error",
                        }),
                });
            });
        },
        [dirPath, env.rpc, model, setErrorMsg, transferModel]
    );

    const handleNativeDragOver = useCallback(
        (event: React.DragEvent<HTMLDivElement>) => {
            if (!hasNativeFiles(event.dataTransfer) || dirPath === "") {
                return;
            }
            event.preventDefault();
            event.stopPropagation();
            event.dataTransfer.dropEffect = "copy";
            setNativeDragActive(true);
        },
        [dirPath]
    );

    const handleNativeDragLeave = useCallback((event: React.DragEvent<HTMLDivElement>) => {
        const relatedTarget = event.relatedTarget as Node;
        if (relatedTarget != null && event.currentTarget.contains(relatedTarget)) {
            return;
        }
        setNativeDragActive(false);
    }, []);

    const handleNativeDrop = useCallback(
        (event: React.DragEvent<HTMLDivElement>) => {
            if (!hasNativeFiles(event.dataTransfer) || dirPath === "") {
                return;
            }
            event.preventDefault();
            event.stopPropagation();
            setNativeDragActive(false);
            Array.from(event.dataTransfer.files).forEach((file) => startUpload(file));
        },
        [dirPath, startUpload]
    );

    return (
        <Fragment>
            <div
                ref={refs.setReference}
                className={clsx("dir-table-container", nativeDragActive && "dir-native-drop-active")}
                onDragOver={handleNativeDragOver}
                onDragLeave={handleNativeDragLeave}
                onDrop={handleNativeDrop}
                onChangeCapture={(e) => {
                    const event = e as React.ChangeEvent<HTMLInputElement>;
                    if (!entryManagerProps) {
                        setSearchText(event.target.value.toLowerCase());
                    }
                }}
                {...getReferenceProps()}
                onContextMenu={(e) => handleFileContextMenu(e)}
                onClick={() => setEntryManagerProps(undefined)}
            >
                <DirectoryTree
                    model={model}
                    data={unfilteredData}
                    search={searchText}
                    setSearch={setSearchText}
                    setSelectedPath={setSelectedEntry}
                    dirPath={dirPath}
                    refreshVersion={refreshVersion}
                    entryManagerOverlayPropsAtom={entryManagerPropsAtom}
                    newFile={newFile}
                    newDirectory={newDirectory}
                />
                {nativeDragActive && (
                    <div className="dir-upload-drop-overlay">
                        <i className="fa-solid fa-cloud-arrow-up" />
                        <span>Drop files to upload</span>
                    </div>
                )}
            </div>
            {entryManagerProps && (
                <EntryManagerOverlay
                    {...entryManagerProps}
                    forwardRef={refs.setFloating}
                    style={floatingStyles}
                    getReferenceProps={getFloatingProps}
                    onCancel={() => setEntryManagerProps(undefined)}
                />
            )}
        </Fragment>
    );
}

export { DirectoryPreview };
