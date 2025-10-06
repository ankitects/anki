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
    options: PostProtoOptions = {},
): Promise<T> {
    try {
        const inputBytes = input.toBinary();
        const path = `/_anki/${method}`;
        const outputBytes = await postProtoInner(path, inputBytes);
        return outputType.fromBinary(outputBytes);
    } catch (err) {
        const { alertOnError = true } = options;
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
        // it is ugly to show internall server client status to user, we should just show error's to user
        throw new Error(process.env.NODE_ENV === "production"?`${msg}`:`${result.status}: ${msg}`);
    }
    const blob = await result.blob();
    const respBuf = await new Response(blob).arrayBuffer();
    return new Uint8Array(respBuf);
}
