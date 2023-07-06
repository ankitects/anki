// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ImportResponse_Note } from "@tslib/anki/import_export_pb";

export type LogQueue = {
    notes: ImportResponse_Note[];
    reason: string;
};

export type SummarizedLogQueues = {
    queues: LogQueue[];
    action: string;
    summary_template: (args: { val: string | number }) => string;
    canBrowse: boolean;
    icon: unknown;
};

export type NoteRow = {
    summary: SummarizedLogQueues;
    queue: LogQueue;
    note: ImportResponse_Note;
};
