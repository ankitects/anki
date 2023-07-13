// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ImportResponse_Log } from "@tslib/anki/import_export_pb";
import { CsvMetadata_DupeResolution } from "@tslib/anki/import_export_pb";
import type { NoteId } from "@tslib/anki/notes_pb";
import { bridgeCommand } from "@tslib/bridgecommand";
import * as tr from "@tslib/ftl";

import { alphaXBox, checkBold, newBox, updateIcon } from "./icons";
import type { LogQueue, NoteRow, SummarizedLogQueues } from "./types";

function getFirstFieldQueue(log: ImportResponse_Log): {
    action: string;
    queue: LogQueue;
} {
    let reason: string;
    let action: string;
    if (log.dupeResolution === CsvMetadata_DupeResolution.DUPLICATE) {
        reason = tr.importingDuplicateNoteAdded();
        action = tr.addingAdded();
    } else if (log.dupeResolution === CsvMetadata_DupeResolution.PRESERVE) {
        reason = tr.importingExistingNoteSkipped();
        action = tr.importingSkipped();
    } else {
        reason = tr.importingNoteUpdatedAsFileHadNewer();
        action = tr.importingUpdated();
    }
    const queue: LogQueue = {
        reason,
        notes: log.firstFieldMatch,
    };
    return { action, queue };
}

export function getSummaries(log: ImportResponse_Log): SummarizedLogQueues[] {
    const summarizedQueues = [
        {
            queues: [
                {
                    notes: log.new,
                    reason: tr.importingAddedNewNote(),
                },
            ],
            action: tr.addingAdded(),
            summaryTemplate: tr.importingNotesAdded,
            canBrowse: true,
            icon: newBox,
        },
        {
            queues: [
                {
                    notes: log.duplicate,
                    reason: tr.importingExistingNoteSkipped(),
                },
            ],
            action: tr.importingSkipped(),
            summaryTemplate: tr.importingExistingNotesSkipped,
            canBrowse: true,
            icon: checkBold,
        },
        {
            queues: [
                {
                    notes: log.conflicting,
                    reason: tr.importingNoteSkippedUpdateDueToNotetype(),
                },
                {
                    notes: log.missingNotetype,
                    reason: tr.importingNoteSkippedDueToMissingNotetype(),
                },
                {
                    notes: log.missingDeck,
                    reason: tr.importingNoteSkippedDueToMissingDeck(),
                },
                {
                    notes: log.emptyFirstField,
                    reason: tr.importingNoteSkippedDueToEmptyFirstField(),
                },
            ],
            action: tr.importingSkipped(),
            summaryTemplate: tr.importingConflictingNotesSkipped,
            canBrowse: false,
            icon: alphaXBox,
        },
        {
            queues: [
                {
                    notes: log.updated,
                    reason: tr.importingNoteUpdatedAsFileHadNewer(),
                },
            ],
            action: tr.importingUpdated(),
            summaryTemplate: tr.importingNotesUpdated,
            canBrowse: true,
            icon: updateIcon,
        },
    ];
    const firstFieldQueue = getFirstFieldQueue(log);
    for (const summary of summarizedQueues) {
        if (summary.action === firstFieldQueue.action) {
            summary.queues.push(firstFieldQueue.queue);
            break;
        }
    }
    return summarizedQueues;
}

export function getRows(summaries: SummarizedLogQueues[]): NoteRow[] {
    const rows: NoteRow[] = [];
    for (const summary of summaries) {
        for (const queue of summary.queues) {
            if (queue.notes) {
                for (const note of queue.notes) {
                    rows.push({ summary, queue, note });
                }
            }
        }
    }
    return rows;
}

export function showInBrowser(ids: (NoteId | undefined)[]): void {
    const nids = ids.map((id) => id!.nid);
    bridgeCommand(`browse:${nids.join(",")}`);
}
