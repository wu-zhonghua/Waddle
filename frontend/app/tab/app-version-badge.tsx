// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { Tooltip } from "@/app/element/tooltip";
import { memo } from "react";

interface AppVersionBadgeProps {
    version?: string | null;
}

const AppVersionBadgeComponent = ({ version }: AppVersionBadgeProps) => {
    if (!version) {
        return null;
    }

    return (
        <Tooltip
            content={`Waddle v${version}`}
            placement="bottom"
            divClassName="flex h-[22px] px-2 mb-1 items-center text-[11px] font-medium leading-none text-secondary/70 select-none whitespace-nowrap"
            divStyle={{ WebkitAppRegion: "no-drag" } as React.CSSProperties}
        >
            v{version}
        </Tooltip>
    );
};
AppVersionBadgeComponent.displayName = "AppVersionBadge";

export const AppVersionBadge = memo(AppVersionBadgeComponent);
