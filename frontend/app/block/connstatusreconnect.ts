// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

export type WshReconnectOperations = {
    reinstall: () => Promise<void>;
    disconnect: () => Promise<void>;
    connect: () => Promise<void>;
};

export function makeWshReconnect(operations: WshReconnectOperations): () => Promise<void> {
    let activeReconnect: Promise<void>;

    return () => {
        if (activeReconnect) {
            return activeReconnect;
        }
        activeReconnect = (async () => {
            await operations.reinstall();
            try {
                await operations.disconnect();
            } catch {
                // Reinstalling the helper may already have stopped the connection.
            }
            await operations.connect();
        })().finally(() => {
            activeReconnect = null;
        });
        return activeReconnect;
    };
}
