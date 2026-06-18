// Copyright 2025, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import type { WaddleConfigViewModel } from "@/app/view/waveconfig/waveconfig-model";
import { memo } from "react";

interface WaddleAIVisualContentProps {
    model: WaddleConfigViewModel;
}

export const WaddleAIVisualContent = memo(({ model }: WaddleAIVisualContentProps) => {
    return (
        <div className="flex flex-col gap-4 p-6 h-full">
            <div className="text-lg font-semibold">Waddle AI Modes - Visual Editor</div>
            <div className="text-muted-foreground">Visual editor coming soon...</div>
        </div>
    );
});

WaddleAIVisualContent.displayName = "WaddleAIVisualContent";