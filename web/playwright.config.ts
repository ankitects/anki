// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { defineConfig } from "@playwright/test";

const MEDIASRV_PORT = process.env.ANKI_API_PORT ?? "40000";

const PYENV_PYTHON = process.platform === "win32"
    ? "out\\pyenv\\Scripts\\python.exe"
    : "out/pyenv/bin/python";

export default defineConfig({
    testDir: "./ts/tests/e2e",
    outputDir: "./out/e2e-report/",
    fullyParallel: false,
    workers: 1,
    forbidOnly: !!process.env.CI,
    retries: 0,
    reporter: process.env.CI
        ? [["github"], ["html", { open: "never", outputFolder: "out/e2e-report" }]]
        : "list",
    use: {
        baseURL: `http://127.0.0.1:${MEDIASRV_PORT}`,
        trace: "retain-on-failure",
        screenshot: "only-on-failure",
    },
    webServer: {
        command: `${PYENV_PYTHON} qt/tests/launch_anki_for_e2e.py`,
        // /favicon.ico responds as soon as the HTTP server thread starts,
        // before the profile's collection has finished loading. /_anki/readyz
        // only returns 200 once the collection is open, so tests never race
        // against the async profile-load that follows server startup.
        url: `http://127.0.0.1:${MEDIASRV_PORT}/_anki/readyz`,
        timeout: 60_000,
        reuseExistingServer: process.env.ANKI_E2E_REUSE_SERVER === "1",
        stdout: "pipe",
        stderr: "pipe",
        env: { ANKI_API_PORT: MEDIASRV_PORT },
    },
});
