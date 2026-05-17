// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface PostProtoOptions {
    /** True by default. Shows a dialog with the error message, then rethrows. */
    alertOnError?: boolean;
    /** If this is set, the request can be canceled by calling abort() on the corresponding AbortController. */
    signal?: AbortSignal;
}

export async function postProto<T>(
    method: string,
    input: { toBinary(): Uint8Array; getType(): { typeName: string } },
    outputType: { fromBinary(arr: Uint8Array): T },
    options: PostProtoOptions = {},
    opChangesType = 0,
): Promise<T> {
    const { alertOnError = true, signal = undefined } = options;
    try {
        const inputBytes = input.toBinary();
        const path = `/_anki/${method}`;
        const outputBytes = await postProtoInner(path, inputBytes, opChangesType, signal);
        return outputType.fromBinary(outputBytes);
    } catch (err) {
        if (
            alertOnError && !(err instanceof Error && (err.message === "500: Interrupted" || err.name === "AbortError"))
        ) {
            alert(err);
        }
        throw err;
    }
}

async function postProtoInner(
    url: string,
    body: Uint8Array,
    opChangesType: number,
    signal?: AbortSignal,
): Promise<Uint8Array> {
    const result = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/binary", "Anki-Op-Changes": opChangesType.toString() },
        body,
        signal,
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
