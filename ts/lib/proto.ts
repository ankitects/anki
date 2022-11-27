// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { Message, rpc, RPCImpl, RPCImplCallback } from "protobufjs";

import { anki } from "../../out/ts/lib/backend_proto";

import Cards = anki.cards;
import Collection = anki.collection;
import DeckConfig = anki.deckconfig;
import Decks = anki.decks;
import Generic = anki.generic;
import I18n = anki.i18n;
import ImportExport = anki.import_export;
import Notes = anki.notes;
import Notetypes = anki.notetypes;
import Scheduler = anki.scheduler;
import Stats = anki.stats;
import Tags = anki.tags;

export { Cards, Collection, Decks, Generic, Notes };

export const empty = Generic.Empty.create();

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

        const blob = await result.blob();
        const respBuf = await new Response(blob).arrayBuffer();
        const uint8Array = new Uint8Array(respBuf);

        callback(null, uint8Array);
    } catch (error) {
        console.log("error caught");
        callback(error as Error, null);
    }
}

export const decks = Decks.DecksService.create(serviceCallback as RPCImpl);

export { DeckConfig };
export const deckConfig = DeckConfig.DeckConfigService.create(
    serviceCallback as RPCImpl,
);

export { I18n };
export const i18n = I18n.I18nService.create(serviceCallback as RPCImpl);

export { ImportExport };
export const importExport = ImportExport.ImportExportService.create(
    serviceCallback as RPCImpl,
);

export { Notetypes };
export const notetypes = Notetypes.NotetypesService.create(serviceCallback as RPCImpl);

export { Scheduler };
export const scheduler = Scheduler.SchedulerService.create(serviceCallback as RPCImpl);

export { Stats };
export const stats = Stats.StatsService.create(serviceCallback as RPCImpl);

export { Tags };
export const tags = Tags.TagsService.create(serviceCallback as RPCImpl);
