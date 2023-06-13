// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { Message, rpc, RPCImpl, RPCImplCallback } from "protobufjs";

import { anki } from "../../out/ts/lib/backend_proto";

import I18n = anki.i18n;
import Scheduler = anki.scheduler;

export class InternalError extends Error {}

async function serviceCallback(
    method: rpc.ServiceMethod<Message<any>, Message<any>>,
    requestData: Uint8Array,
    callback: RPCImplCallback,
): Promise<void> {
    const headers = new Headers();
    headers.set("Content-type", "application/octet-stream");

    const methodName = method.name[0].toLowerCase() + method.name.substring(1);
    const path = `/_anki/${methodName}`;

    try {
        const result = await fetch(path, {
            method: "POST",
            headers,
            body: requestData,
        });

        if (result.status == 500) {
            callback(new InternalError(await result.text()), null);
            return;
        }

        const blob = await result.blob();
        const respBuf = await new Response(blob).arrayBuffer();
        const uint8Array = new Uint8Array(respBuf);

        callback(null, uint8Array);
    } catch (error) {
        console.log("error caught");
        callback(error as Error, null);
    }
}

export { I18n };
export const i18n = I18n.I18nService.create(serviceCallback as RPCImpl);

export { Scheduler };
export const scheduler = Scheduler.SchedulerService.create(serviceCallback as RPCImpl);
