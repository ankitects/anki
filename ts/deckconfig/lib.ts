// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import pb from "anki/backend_proto";
import { postRequest } from "anki/postrequest";
import { Writable, writable, get, Readable, readable } from "svelte/store";
import { isEqual, cloneDeep } from "lodash-es";
import * as tr from "anki/i18n";

export async function getDeckConfigInfo(
    deckId: number
): Promise<pb.BackendProto.DeckConfigForUpdate> {
    return pb.BackendProto.DeckConfigForUpdate.decode(
        await postRequest("/_anki/deckConfigForUpdate", JSON.stringify({ deckId }))
    );
}

export type DeckConfigId = number;

export interface ConfigWithCount {
    config: pb.BackendProto.DeckConfig;
    useCount: number;
}

export interface ParentLimits {
    newCards: number;
    reviews: number;
}

/// Info for showing the top selector
export interface ConfigListEntry {
    idx: number;
    name: string;
    useCount: number;
    current: boolean;
}

type ConfigInner = pb.BackendProto.DeckConfig.Config;
export class DeckConfigState {
    readonly currentConfig: Writable<ConfigInner>;
    readonly configList: Readable<ConfigListEntry[]>;
    readonly parentLimits: Readable<ParentLimits>;
    readonly currentDeck: pb.BackendProto.DeckConfigForUpdate.CurrentDeck;
    readonly defaults: ConfigInner;

    private configs: ConfigWithCount[];
    private selectedIdx: number;
    private configListSetter?: (val: ConfigListEntry[]) => void;
    private parentLimitsSetter?: (val: ParentLimits) => void;
    private removedConfigs: DeckConfigId[] = [];

    constructor(data: pb.BackendProto.DeckConfigForUpdate) {
        this.currentDeck = data.currentDeck as pb.BackendProto.DeckConfigForUpdate.CurrentDeck;
        this.defaults = data.defaults!.config! as ConfigInner;
        this.configs = data.allConfig.map((config) => {
            return {
                config: config.config as pb.BackendProto.DeckConfig,
                useCount: config.useCount!,
            };
        });
        this.selectedIdx = Math.max(
            0,
            this.configs.findIndex((c) => c.config.id === this.currentDeck.configId)
        );

        // decrement the use count of the starting item, as we'll apply +1 to currently
        // selected one at display time
        this.configs[this.selectedIdx].useCount -= 1;
        this.currentConfig = writable(this.getCurrentConfig());
        this.configList = readable(this.getConfigList(), (set) => {
            this.configListSetter = set;
            return;
        });
        this.parentLimits = readable(this.getParentLimits(), (set) => {
            this.parentLimitsSetter = set;
            return;
        });
    }

    setCurrentIndex(index: number): void {
        this.saveCurrentConfig();
        this.selectedIdx = index;
        this.updateCurrentConfig();
        // use counts have changed
        this.updateConfigList();
    }

    /// Persist any changes made to the current config into the list of configs.
    saveCurrentConfig(): void {
        const config = get(this.currentConfig);
        if (!isEqual(config, this.configs[this.selectedIdx].config.config)) {
            console.log("save");
            this.configs[this.selectedIdx].config.config = config;
            this.configs[this.selectedIdx].config.mtimeSecs = 0;
        } else {
            console.log("no changes");
        }
    }

    getCurrentName(): string {
        return this.configs[this.selectedIdx].config.name;
    }

    setCurrentName(name: string): void {
        if (this.configs[this.selectedIdx].config.name === name) {
            return;
        }
        const uniqueName = this.ensureNewNameUnique(name);
        this.configs[this.selectedIdx].config.name = uniqueName;
        this.configs[this.selectedIdx].config.mtimeSecs = 0;
        this.updateConfigList();
    }

    /// Adds a new config, making it current.
    /// not already a new config.
    addConfig(name: string): void {
        const uniqueName = this.ensureNewNameUnique(name);
        const config = pb.BackendProto.DeckConfig.create({
            id: 0,
            name: uniqueName,
            config: cloneDeep(this.defaults),
        });
        const configWithCount = { config, useCount: 0 };
        this.configs.push(configWithCount);
        this.selectedIdx = this.configs.length - 1;
        this.updateCurrentConfig();
        this.updateConfigList();
    }

    /// Will throw if the default deck is selected.
    removeCurrentConfig(): void {
        const currentId = this.configs[this.selectedIdx].config.id;
        if (currentId === 1) {
            throw "can't remove default config";
        }

        if (currentId !== 0) {
            this.removedConfigs.push(currentId);
        }
        this.configs.splice(this.selectedIdx, 1);
        this.selectedIdx = Math.max(0, this.selectedIdx - 1);
        this.updateCurrentConfig();
        this.updateConfigList();
    }

    private ensureNewNameUnique(name: string): string {
        if (this.configs.find((e) => e.config.name === name) !== undefined) {
            return name + (new Date().getTime() / 1000).toFixed(0);
        } else {
            return name;
        }
    }

    private updateCurrentConfig(): void {
        this.currentConfig.set(this.getCurrentConfig());
        this.parentLimitsSetter?.(this.getParentLimits());
    }

    private updateConfigList(): void {
        this.configListSetter?.(this.getConfigList());
    }

    /// Returns a copy of the currently selected config.
    private getCurrentConfig(): ConfigInner {
        return cloneDeep(this.configs[this.selectedIdx].config.config as ConfigInner);
    }

    private getConfigList(): ConfigListEntry[] {
        const list: ConfigListEntry[] = this.configs.map((c, idx) => {
            const useCount = c.useCount + (idx === this.selectedIdx ? 1 : 0);
            return {
                name: c.config.name,
                current: idx === this.selectedIdx,
                idx,
                useCount,
            };
        });
        list.sort((a, b) =>
            a.name.localeCompare(b.name, tr.i18n.langs, { sensitivity: "base" })
        );
        return list;
    }

    private getParentLimits(): ParentLimits {
        const parentConfigs = this.configs.filter((c) =>
            this.currentDeck.parentConfigIds.includes(c.config.id)
        );
        const newCards = parentConfigs.reduce(
            (previous, current) =>
                Math.min(previous, current.config.config?.newPerDay ?? 0),
            2 ** 31
        );
        const reviews = parentConfigs.reduce(
            (previous, current) =>
                Math.min(previous, current.config.config?.reviewsPerDay ?? 0),
            2 ** 31
        );
        return {
            newCards,
            reviews,
        };
    }
}
