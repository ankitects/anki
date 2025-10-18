// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface PostProtoOptions {
    /** True by default. Shows a dialog with the error message, then rethrows. */
    alertOnError?: boolean;
    // whether to use the "anki:" custom protocol or not
    customProtocol?: boolean;
}

const IS_WINDOWS = navigator.platform.startsWith("Win");
const CUSTOM_PROTOCOL_URI = IS_WINDOWS ? "http://anki.localhost" : "anki://localhost";

export async function postProto<T>(
    method: string,
    input: { toBinary(): Uint8Array; getType(): { typeName: string } },
    outputType: { fromBinary(arr: Uint8Array): T },
    options: PostProtoOptions = {},
): Promise<T> {
    const { alertOnError = true, customProtocol = false } = options;
    try {
        const inputBytes = input.toBinary();
        const backendUrl = customProtocol ? CUSTOM_PROTOCOL_URI : "/_anki";
        const path = `${backendUrl}/${method}`;
        const outputBytes = await postProtoInner(path, inputBytes);
        return outputType.fromBinary(outputBytes);
    } catch (err) {
        if (alertOnError && !(err instanceof Error && err.message === "500: Interrupted")) {
            alert(err);
        }
        throw err;
    }
}

async function postProtoInner(url: string, body: Uint8Array): Promise<Uint8Array> {
    const result = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/binary",
        },
        body,
    });
    if (!result.ok) {
        let msg = "something went wrong";
        try {
            msg = await result.text();
        } catch {
            // ignore
        }
        throw new Error(`${result.status}: ${msg}`);
    }
    const blob = await result.blob();
    const respBuf = await new Response(blob).arrayBuffer();
    return new Uint8Array(respBuf);
}
