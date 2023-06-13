// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface PostProtoOptions {
    /** True by default. Shows a dialog with the error message, then rethrows. */
    alertOnError?: boolean;
}

export async function postProto<T>(
    method: string,
    input: { toBinary(): Uint8Array; getType(): { typeName: string } },
    outputType: { fromBinary(arr: Uint8Array): T },
    { alertOnError = true }: PostProtoOptions,
): Promise<T> {
    try {
        const inputBytes = input.toBinary();
        const path = `/_anki/${method}`;
        const outputBytes = await postProtoInner(path, inputBytes);
        return outputType.fromBinary(outputBytes);
    } catch (err) {
        if (alertOnError) {
            alert(err);
        }
        throw err;
    }
}

async function postProtoInner(url: string, body: Uint8Array): Promise<Uint8Array> {
    const result = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/octet-stream",
        },
        body,
    });
    if (!result.ok) {
        throw new Error(`unexpected response code: ${result.status}`);
    }
    const blob = await result.blob();
    const respBuf = await new Response(blob).arrayBuffer();
    return new Uint8Array(respBuf);
}
