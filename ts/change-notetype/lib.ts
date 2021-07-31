// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { Notetypes } from "lib/proto";
import { postRequest } from "lib/postrequest";
import { readable, Readable } from "svelte/store";
import { isEqual } from "lodash-es";

export async function getNotetypeNames(): Promise<Notetypes.NotetypeNames> {
    return Notetypes.NotetypeNames.decode(
        await postRequest("/_anki/notetypeNames", "")
    );
}

export async function getChangeNotetypeInfo(
    oldNotetypeId: number,
    newNotetypeId: number
): Promise<Notetypes.ChangeNotetypeInfo> {
    return Notetypes.ChangeNotetypeInfo.decode(
        await postRequest(
            "/_anki/changeNotetypeInfo",
            JSON.stringify({ oldNotetypeId, newNotetypeId })
        )
    );
}

export async function changeNotetype(
    input: Notetypes.ChangeNotetypeRequest
): Promise<void> {
    const data: Uint8Array = Notetypes.ChangeNotetypeRequest.encode(input).finish();
    await postRequest("/_anki/changeNotetype", data);
    return;
}

function nullToNegativeOne(list: (number | null)[]): number[] {
    return list.map((val) => val ?? -1);
}

/// Public only for tests.
export function negativeOneToNull(list: number[]): (number | null)[] {
    return list.map((val) => (val === -1 ? null : val));
}

/// Wrapper for the protobuf message to make it more ergonomic.
export class ChangeNotetypeInfoWrapper {
    fields: (number | null)[];
    templates?: (number | null)[];
    readonly info: Notetypes.ChangeNotetypeInfo;

    constructor(info: Notetypes.ChangeNotetypeInfo) {
        this.info = info;
        const templates = info.input!.newTemplates!;
        if (templates.length > 0) {
            this.templates = negativeOneToNull(templates);
        }
        this.fields = negativeOneToNull(info.input!.newFields!);
    }

    /// A list with an entry for each field/template in the new notetype, with
    /// the values pointing back to indexes in the original notetype.
    mapForContext(ctx: MapContext): (number | null)[] {
        return ctx == MapContext.Template ? this.templates ?? [] : this.fields;
    }

    /// Return index of old fields/templates, with null values mapped to "Nothing"
    /// at the end.
    getOldIndex(ctx: MapContext, newIdx: number): number {
        const map = this.mapForContext(ctx);
        const val = map[newIdx];
        return val ?? this.getOldNamesIncludingNothing(ctx).length - 1;
    }

    /// Return all the old names, with "Nothing" at the end.
    getOldNamesIncludingNothing(ctx: MapContext): string[] {
        return [...this.getOldNames(ctx), "(Nothing)"];
    }

    /// Old names without "Nothing" at the end.
    getOldNames(ctx: MapContext): string[] {
        return ctx == MapContext.Template
            ? this.info.oldTemplateNames
            : this.info.oldFieldNames;
    }

    getNewName(ctx: MapContext, idx: number): string {
        return (
            ctx == MapContext.Template
                ? this.info.newTemplateNames
                : this.info.newFieldNames
        )[idx];
    }

    unusedItems(ctx: MapContext): string[] {
        const usedEntries = new Set(this.mapForContext(ctx).filter((v) => v !== null));
        const oldNames = this.getOldNames(ctx);
        const unusedIdxs = [...Array(oldNames.length).keys()].filter(
            (idx) => !usedEntries.has(idx)
        );
        const unusedNames = unusedIdxs.map((idx) => oldNames[idx]);
        unusedNames.sort();
        return unusedNames;
    }

    unchanged(): boolean {
        return (
            this.input().newNotetypeId === this.input().oldNotetypeId &&
            isEqual(this.fields, [...Array(this.fields.length).keys()]) &&
            isEqual(this.templates, [...Array(this.templates?.length ?? 0).keys()])
        );
    }

    input(): Notetypes.ChangeNotetypeRequest {
        return this.info.input as Notetypes.ChangeNotetypeRequest;
    }

    /// Pack changes back into input message for saving.
    intoInput(): Notetypes.ChangeNotetypeRequest {
        const input = this.info.input as Notetypes.ChangeNotetypeRequest;
        input.newFields = nullToNegativeOne(this.fields);
        if (this.templates) {
            input.newTemplates = nullToNegativeOne(this.templates);
        }

        return input;
    }
}

export interface NotetypeListEntry {
    idx: number;
    name: string;
    current: boolean;
}

export enum MapContext {
    Field,
    Template,
}
export class ChangeNotetypeState {
    readonly info: Readable<ChangeNotetypeInfoWrapper>;
    readonly notetypes: Readable<NotetypeListEntry[]>;

    private info_: ChangeNotetypeInfoWrapper;
    private infoSetter!: (val: ChangeNotetypeInfoWrapper) => void;
    private notetypeNames: Notetypes.NotetypeNames;
    private notetypesSetter!: (val: NotetypeListEntry[]) => void;

    constructor(
        notetypes: Notetypes.NotetypeNames,
        info: Notetypes.ChangeNotetypeInfo
    ) {
        this.info_ = new ChangeNotetypeInfoWrapper(info);
        this.info = readable(this.info_, (set) => {
            this.infoSetter = set;
        });
        this.notetypeNames = notetypes;
        this.notetypes = readable(this.buildNotetypeList(), (set) => {
            this.notetypesSetter = set;
            return;
        });
    }

    async setTargetNotetypeIndex(idx: number): Promise<void> {
        this.info_.input().newNotetypeId = this.notetypeNames.entries[idx].id!;
        this.notetypesSetter(this.buildNotetypeList());
        const newInfo = await getChangeNotetypeInfo(
            this.info_.input().oldNotetypeId,
            this.info_.input().newNotetypeId
        );

        this.info_ = new ChangeNotetypeInfoWrapper(newInfo);
        this.info_.unusedItems(MapContext.Field);
        this.infoSetter(this.info_);
    }

    setOldIndex(ctx: MapContext, newIdx: number, oldIdx: number): void {
        const list = this.info_.mapForContext(ctx);
        const oldNames = this.info_.getOldNames(ctx);
        const realOldIdx = oldIdx < oldNames.length ? oldIdx : null;
        const allowDupes = ctx == MapContext.Field;

        // remove any existing references?
        if (!allowDupes && realOldIdx !== null) {
            for (let i = 0; i < list.length; i++) {
                if (list[i] === realOldIdx) {
                    list[i] = null;
                }
            }
        }

        list[newIdx] = realOldIdx;
        this.infoSetter(this.info_);
    }

    async save(): Promise<void> {
        if (this.info_.unchanged()) {
            alert("No changes to save");
            return;
        }
        await changeNotetype(this.dataForSaving());
    }

    dataForSaving(): Notetypes.ChangeNotetypeRequest {
        return this.info_.intoInput();
    }

    private buildNotetypeList(): NotetypeListEntry[] {
        const currentId = this.info_.input().newNotetypeId;
        return this.notetypeNames.entries.map(
            (entry, idx) =>
                ({
                    idx,
                    name: entry.name,
                    current: entry.id === currentId,
                } as NotetypeListEntry)
        );
    }
}
