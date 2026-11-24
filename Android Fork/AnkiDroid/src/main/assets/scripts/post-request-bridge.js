// SPDX-License-Identifier: GPL-3.0-or-later
"use strict";

/**
 * Sends same-origin POST requests made with `fetch()` to AnkiDroid through a WebMessageListener,
 * as Android's WebView can't intercept request bodies.
 *
 * `XMLHttpRequest`, form and web worker requests aren't sent: this script only runs in frames.
 *
 * Messages are binary, with big-endian numbers:
 * - request: request ID (4 bytes), URI length (2 bytes), UTF-8 URI, body
 * - reply: request ID (4 bytes), HTTP status (2 bytes), body
 *
 * @see com.ichi2.anki.pages.PostRequestBridge
 */
(() => {
    /** Injected by `PostRequestBridge` */
    const bridge = globalThis.ankidroidPostRequestBridge;
    if (!bridge) {
        return;
    }

    const HEADER_SIZE = 6;
    const encoder = new TextEncoder();
    /** @type {Map<number, (response: Response) => void>} */
    const pendingRequests = new Map();
    let lastRequestId = 0;
    // not `location.origin`, which is "null" in srcdoc iframes
    const pageOrigin = globalThis.origin;

    bridge.onmessage = ({ data }) => {
        const header = new DataView(data, 0, HEADER_SIZE);
        const id = header.getUint32(0);
        const response = new Response(new Uint8Array(data, HEADER_SIZE), {
            status: header.getUint16(4),
        });
        // the request may have been aborted
        pendingRequests.get(id)?.(response);
        pendingRequests.delete(id);
    };

    const originalFetch = globalThis.fetch.bind(globalThis);

    globalThis.fetch = async (input, init) => {
        if (!isLocalPostRequest(input, init)) {
            return originalFetch(input, init);
        }
        const request = new Request(input, init);
        const { signal } = request;
        const body = new Uint8Array(await request.arrayBuffer());
        // checked after reading the body, as the request may be aborted meanwhile
        signal.throwIfAborted();
        const id = ++lastRequestId;
        const message = encodeRequest(id, new URL(request.url).pathname, body);
        return new Promise((resolve, reject) => {
            const onAbort = () => {
                pendingRequests.delete(id);
                reject(signal.reason);
            };
            signal.addEventListener("abort", onAbort, { once: true });
            pendingRequests.set(id, response => {
                signal.removeEventListener("abort", onAbort);
                resolve(response);
            });
            bridge.postMessage(message);
        });
    };

    /**
     * @param {RequestInfo | URL} input
     * @param {RequestInit | undefined} init
     */
    function isLocalPostRequest(input, init) {
        const method = init?.method ?? (input instanceof Request ? input.method : "GET");
        const url = new URL(input instanceof Request ? input.url : input, document.baseURI);
        return method.toUpperCase() === "POST" && url.origin === pageOrigin;
    }

    /**
     * @param {number} id
     * @param {string} uri
     * @param {Uint8Array} body
     * @returns {ArrayBuffer}
     * @throws {TypeError} if the URI is too long for the 2 bytes of its length
     */
    function encodeRequest(id, uri, body) {
        const uriBytes = encoder.encode(uri);
        if (uriBytes.length > 0xffff) {
            throw new TypeError("Request URL too long");
        }
        const message = new Uint8Array(HEADER_SIZE + uriBytes.length + body.length);
        const header = new DataView(message.buffer, 0, HEADER_SIZE);
        header.setUint32(0, id);
        header.setUint16(4, uriBytes.length);
        message.set(uriBytes, HEADER_SIZE);
        message.set(body, HEADER_SIZE + uriBytes.length);
        return message.buffer;
    }
})();
