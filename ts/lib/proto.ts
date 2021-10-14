// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { anki } from "./backend_proto";

import Cards = anki.cards;
import DeckConfig = anki.deckconfig;
import Generic = anki.generic;
import Notetypes = anki.notetypes;
import Scheduler = anki.scheduler;
import Stats = anki.stats;
import Tags = anki.tags;

export { Stats, Cards, DeckConfig, Notetypes, Scheduler, Tags };

export function unwrapOptionalNumber(
    msg:
        | Generic.IInt64
        | Generic.IUInt32
        | Generic.IInt32
        | Generic.OptionalInt32
        | Generic.OptionalUInt32
        | null
        | undefined
): number | undefined {
    if (msg && msg !== null) {
        if (msg.val !== null) {
            return msg.val;
        }
    }
    return undefined;
}
