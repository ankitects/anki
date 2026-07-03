// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ImportResponse_Log, ImportResponse_Note } from "@generated/anki/import_export_pb";
import { CsvMetadata_DupeResolution } from "@generated/anki/import_export_pb";
import { searchInBrowser } from "@generated/backend";
import * as tr from "@generated/ftl";

import { checkCircle, closeBox, newBox, updateIcon } from "$lib/components/icons";

import type { LogQueue, NoteRow, SummarizedLogQueues } from "./types";

function getFirstFieldQueue(log: ImportResponse_Log): {
    action: string;
    queue: LogQueue;
} {
    let reason: string;
    let action: string;
    if (log.dupeResolution === CsvMetadata_DupeResolution.DUPLICATE) {
        reason = tr.importingDuplicateNoteAdded();
        action = tr.importingAdded();
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
            icon: checkCircle,
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
        {
            queues: [
                {
                    notes: log.conflicting,
                    reason: tr.importingNoteSkippedUpdateDueToNotetype2(),
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
            summaryTemplate: tr.importingNotesFailed,
            canBrowse: false,
            icon: closeBox,
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

export function showInBrowser(notes: ImportResponse_Note[]): void {
    searchInBrowser({
        filter: {
            case: "nids",
            value: { ids: notes.map((note) => note.id!.nid) },
        },
    });
}
