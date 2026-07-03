// Copyright 2026, Command Line Inc.
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("GitHub release workflow", () => {
    it("publishes releases with updater assets", () => {
        const workflow = readFileSync(".github/workflows/build-helper.yml", "utf8");

        expect(workflow).toContain("name: Waddle ${{ github.ref_name }} Release");
        expect(workflow).toContain("draft: false");
        expect(workflow).toContain("make/*.yml");
        expect(workflow).toContain("make/*.blockmap");
    });

    it("skips optional release integrations when their secrets are unavailable", () => {
        const workflow = readFileSync(".github/workflows/build-helper.yml", "utf8");

        expect(workflow).toContain(
            "WINDOWS_SIGNING_ENABLED: ${{ secrets.SM_HOST != '' && secrets.SM_API_KEY != '' && secrets.SM_CLIENT_CERT_FILE_B64 != '' && secrets.SM_CLIENT_CERT_PASSWORD != '' && secrets.SM_CODE_SIGNING_CERT_SHA1_HASH != '' }}"
        );
        expect(workflow).toContain(
            "S3_UPLOAD_ENABLED: ${{ secrets.ARTIFACTS_KEY_ID != '' && secrets.ARTIFACTS_KEY_SECRET != '' }}"
        );
        expect(workflow).toContain(
            "if: matrix.platform == 'windows' && github.event_name != 'workflow_dispatch' && env.WINDOWS_SIGNING_ENABLED == 'true'"
        );
        expect(workflow).toContain("if: github.event_name != 'workflow_dispatch' && env.S3_UPLOAD_ENABLED == 'true'");
    });
});
