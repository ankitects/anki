// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export async function postRequest(
    path: string,
    body: string | Uint8Array,
    headers: Record<string, string> = {}
): Promise<Uint8Array> {
    if (body instanceof Uint8Array) {
        headers["Content-type"] = "application/octet-stream";
    }
    const resp = await fetch(path, {
        method: "POST",
        headers,
        body,
    });
    if (!resp.ok) {
        const body = await resp.text();
        throw Error(`${resp.status}: ${body}`);
    }
    // get returned bytes
    const respBlob = await resp.blob();
    const respBuf = await new Response(respBlob).arrayBuffer();
    const bytes = new Uint8Array(respBuf);
    return bytes;
}
