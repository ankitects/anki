// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ImportResponse_Note } from "@generated/anki/import_export_pb";

import type { IconData } from "$lib/components/types";

export type LogQueue = {
    notes: ImportResponse_Note[];
    reason: string;
};

export type SummarizedLogQueues = {
    queues: LogQueue[];
    action: string;
    summaryTemplate: (args: { count: number }) => string;
    canBrowse: boolean;
    icon: IconData;
};

export type NoteRow = {
    summary: SummarizedLogQueues;
    queue: LogQueue;
    note: ImportResponse_Note;
};

type PathParams = {
    type: "json_file";
    path: string;
};

type JsonParams = {
    type: "json_string";
    path: string;
    json: string;
};

export type LogParams = PathParams | JsonParams;
