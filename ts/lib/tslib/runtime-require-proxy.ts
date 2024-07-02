// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// Proxy for requireAsync in runtime-require.ts that is instantly available and
// delegates to the original function once available.
function requireAsyncProxy(name: string): Promise<Record<string, unknown>> {
    return new Promise((resolve) => {
        const intervalId = setInterval(() => {
            if (globalThis.requireAsync !== requireAsyncProxy) {
                clearInterval(intervalId);
                globalThis.requireAsync(name).then(resolve);
            }
        }, 50);
    });
}

Object.assign(globalThis, { requireAsync: requireAsyncProxy });
