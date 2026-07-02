// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChangeNotetypeInfo, ChangeNotetypeRequest, NotetypeNames } from "@generated/anki/notetypes_pb";
import { changeNotetype, getChangeNotetypeInfo } from "@generated/backend";
import * as tr from "@generated/ftl";
import { isEqual } from "lodash-es";
import type { Readable } from "svelte/store";
import { readable } from "svelte/store";

function nullToNegativeOne(list: (number | null)[]): number[] {
    return list.map((val) => val ?? -1);
}

/** Public only for tests. */
export function negativeOneToNull(list: number[]): (number | null)[] {
    return list.map((val) => (val === -1 ? null : val));
}

/** Wrapper for the protobuf message to make it more ergonomic. */
export class ChangeNotetypeInfoWrapper {
    fields: (number | null)[];
    templates?: (number | null)[];
    oldNotetypeName: string;
    isCloze: boolean;
    readonly info: ChangeNotetypeInfo;

    constructor(info: ChangeNotetypeInfo) {
        this.info = info;
        const templates = info.input?.newTemplates ?? [];
        this.isCloze = info.input?.isCloze ?? false;
        if (templates.length > 0) {
            this.templates = negativeOneToNull(templates);
        }
        this.fields = negativeOneToNull(info.input?.newFields ?? []);
        this.oldNotetypeName = info.oldNotetypeName;
    }

    /** A list with an entry for each field/template in the new notetype, with
    the values pointing back to indexes in the original notetype. */
    mapForContext(ctx: MapContext): (number | null)[] {
        return ctx == MapContext.Template ? this.templates ?? [] : this.fields;
    }

    /** Return index of old fields/templates, with null values mapped to "Nothing"
    at the end.*/
    getOldIndex(ctx: MapContext, newIdx: number): number {
        const map = this.mapForContext(ctx);
        const val = map[newIdx];
        return val ?? this.getOldNamesIncludingNothing(ctx).length - 1;
    }

    /** Return all the old names, with "Nothing" at the end. */
    getOldNamesIncludingNothing(ctx: MapContext): string[] {
        return [...this.getOldNames(ctx), tr.changeNotetypeNothing()];
    }

    /** Old names without "Nothing" at the end. */
    getOldNames(ctx: MapContext): string[] {
        return ctx == MapContext.Template
            ? this.info.oldTemplateNames
            : this.info.oldFieldNames;
    }

    getOldNotetypeName(): string {
        return this.info.oldNotetypeName;
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
            (idx) => !usedEntries.has(idx),
        );
        const unusedNames = unusedIdxs.map((idx) => oldNames[idx]);
        return unusedNames;
    }

    unchanged(): boolean {
        return (
            this.input().newNotetypeId === this.input().oldNotetypeId
            && isEqual(this.fields, [...Array(this.fields.length).keys()])
            && isEqual(this.templates, [...Array(this.templates?.length ?? 0).keys()])
        );
    }

    input(): ChangeNotetypeRequest {
        return this.info.input!;
    }

    /** Pack changes back into input message for saving. */
    intoInput(): ChangeNotetypeRequest {
        const input = this.info.input!;
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
    private notetypeNames: NotetypeNames;
    private notetypesSetter!: (val: NotetypeListEntry[]) => void;

    constructor(
        notetypes: NotetypeNames,
        info: ChangeNotetypeInfo,
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
        const { oldNotetypeId, newNotetypeId } = this.info_.input();
        const newInfo = await getChangeNotetypeInfo({
            oldNotetypeId,
            newNotetypeId,
        });
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

    dataForSaving(): ChangeNotetypeRequest {
        return this.info_.intoInput();
    }

    async save(): Promise<void> {
        if (this.info_.unchanged()) {
            alert("No changes to save");
            return;
        }
        await changeNotetype(this.dataForSaving());
    }

    private buildNotetypeList(): NotetypeListEntry[] {
        const currentId = this.info_.input().newNotetypeId;
        return this.notetypeNames.entries.map(
            (entry, idx) => ({
                idx,
                name: entry.name,
                current: entry.id === currentId,
            } satisfies NotetypeListEntry),
        );
    }
}
