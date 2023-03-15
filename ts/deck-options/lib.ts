// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { localeCompare } from "@tslib/i18n";
import { DeckConfig, deckConfig } from "@tslib/proto";
import { cloneDeep, isEqual } from "lodash-es";
import type { Readable, Writable } from "svelte/store";
import { get, readable, writable } from "svelte/store";

import type { DynamicSvelteComponent } from "../sveltelib/dynamicComponent";

export type DeckOptionsId = number;

export interface ConfigWithCount {
    config: DeckConfig.DeckConfig;
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

export class DeckOptionsState {
    readonly currentConfig: Writable<DeckConfig.DeckConfig.Config>;
    readonly currentAuxData: Writable<Record<string, unknown>>;
    readonly configList: Readable<ConfigListEntry[]>;
    readonly parentLimits: Readable<ParentLimits>;
    readonly cardStateCustomizer: Writable<string>;
    readonly currentDeck: DeckConfig.DeckConfigsForUpdate.CurrentDeck;
    readonly deckLimits: Writable<DeckConfig.DeckConfigsForUpdate.CurrentDeck.Limits>;
    readonly defaults: DeckConfig.DeckConfig.Config;
    readonly addonComponents: Writable<DynamicSvelteComponent[]>;
    readonly v3Scheduler: boolean;
    readonly newCardsIgnoreReviewLimit: Writable<boolean>;

    private targetDeckId: number;
    private configs: ConfigWithCount[];
    private selectedIdx: number;
    private configListSetter!: (val: ConfigListEntry[]) => void;
    private parentLimitsSetter!: (val: ParentLimits) => void;
    private modifiedConfigs: Set<DeckOptionsId> = new Set();
    private removedConfigs: DeckOptionsId[] = [];
    private schemaModified: boolean;

    constructor(targetDeckId: number, data: DeckConfig.DeckConfigsForUpdate) {
        this.targetDeckId = targetDeckId;
        this.currentDeck = data.currentDeck!;
        this.defaults = data.defaults!.config!;
        this.configs = data.allConfig.map((config) => {
            const configInner = config.config!;

            return {
                config: configInner,
                useCount: config.useCount!,
            };
        });
        this.selectedIdx = Math.max(
            0,
            this.configs.findIndex((c) => c.config.id === this.currentDeck.configId),
        );
        this.sortConfigs();
        this.v3Scheduler = data.v3Scheduler;
        this.cardStateCustomizer = writable(data.cardStateCustomizer);
        this.deckLimits = writable(data.currentDeck?.limits ?? createLimits());
        this.newCardsIgnoreReviewLimit = writable(data.newCardsIgnoreReviewLimit);

        // decrement the use count of the starting item, as we'll apply +1 to currently
        // selected one at display time
        this.configs[this.selectedIdx].useCount -= 1;
        this.currentConfig = writable(this.getCurrentConfig());
        this.currentAuxData = writable(this.getCurrentAuxData());
        this.configList = readable(this.getConfigList(), (set) => {
            this.configListSetter = set;
            return;
        });
        this.parentLimits = readable(this.getParentLimits(), (set) => {
            this.parentLimitsSetter = set;
            return;
        });
        this.schemaModified = data.schemaModified;
        this.addonComponents = writable([]);

        // create a temporary subscription to force our setters to be set immediately,
        // so unit tests don't get stale results
        get(this.configList);
        get(this.parentLimits);

        // update our state when the current config is changed
        this.currentConfig.subscribe((val) => this.onCurrentConfigChanged(val));
        this.currentAuxData.subscribe((val) => this.onCurrentAuxDataChanged(val));
    }

    setCurrentIndex(index: number): void {
        this.selectedIdx = index;
        this.updateCurrentConfig();
        // use counts have changed
        this.updateConfigList();
    }

    getCurrentName(): string {
        return this.configs[this.selectedIdx].config.name;
    }

    setCurrentName(name: string): void {
        if (this.configs[this.selectedIdx].config.name === name) {
            return;
        }
        const uniqueName = this.ensureNewNameUnique(name);
        const config = this.configs[this.selectedIdx].config;
        config.name = uniqueName;
        if (config.id) {
            this.modifiedConfigs.add(config.id);
        }
        this.sortConfigs();
        this.updateConfigList();
    }

    /// Adds a new config, making it current.
    addConfig(name: string): void {
        this.addConfigFrom(name, this.defaults);
    }

    /// Clone the current config, making it current.
    cloneConfig(name: string): void {
        this.addConfigFrom(name, this.configs[this.selectedIdx].config.config!);
    }

    /// Clone the current config, making it current.
    private addConfigFrom(name: string, source: DeckConfig.DeckConfig.IConfig): void {
        const uniqueName = this.ensureNewNameUnique(name);
        const config = DeckConfig.DeckConfig.create({
            id: 0,
            name: uniqueName,
            config: DeckConfig.DeckConfig.Config.create(cloneDeep(source)),
        });
        const configWithCount = { config, useCount: 0 };
        this.configs.push(configWithCount);
        this.selectedIdx = this.configs.length - 1;
        this.sortConfigs();
        this.updateCurrentConfig();
        this.updateConfigList();
    }

    removalWilLForceFullSync(): boolean {
        return !this.schemaModified && this.configs[this.selectedIdx].config.id !== 0;
    }

    defaultConfigSelected(): boolean {
        return this.configs[this.selectedIdx].config.id === 1;
    }

    /// Will throw if the default deck is selected.
    removeCurrentConfig(): void {
        const currentId = this.configs[this.selectedIdx].config.id;
        if (currentId === 1) {
            throw Error("can't remove default config");
        }
        if (currentId !== 0) {
            this.removedConfigs.push(currentId);
            this.schemaModified = true;
        }
        this.configs.splice(this.selectedIdx, 1);
        this.selectedIdx = Math.max(0, this.selectedIdx - 1);
        this.updateCurrentConfig();
        this.updateConfigList();
    }

    dataForSaving(
        applyToChildren: boolean,
    ): NonNullable<DeckConfig.IUpdateDeckConfigsRequest> {
        const modifiedConfigsExcludingCurrent = this.configs
            .map((c) => c.config)
            .filter((c, idx) => {
                return (
                    idx !== this.selectedIdx
                    && (c.id === 0 || this.modifiedConfigs.has(c.id))
                );
            });
        const configs = [
            ...modifiedConfigsExcludingCurrent,
            // current must come last, even if unmodified
            this.configs[this.selectedIdx].config,
        ];
        return {
            targetDeckId: this.targetDeckId,
            removedConfigIds: this.removedConfigs,
            configs,
            applyToChildren,
            cardStateCustomizer: get(this.cardStateCustomizer),
            limits: get(this.deckLimits),
            newCardsIgnoreReviewLimit: get(this.newCardsIgnoreReviewLimit),
        };
    }

    async save(applyToChildren: boolean): Promise<void> {
        await deckConfig.updateDeckConfigs(
            DeckConfig.UpdateDeckConfigsRequest.create(
                this.dataForSaving(applyToChildren),
            ),
        );
    }

    private onCurrentConfigChanged(config: DeckConfig.DeckConfig.Config): void {
        const configOuter = this.configs[this.selectedIdx].config;
        if (!isEqual(config, configOuter.config)) {
            configOuter.config = config;
            if (configOuter.id) {
                this.modifiedConfigs.add(configOuter.id);
            }
        }
        this.parentLimitsSetter?.(this.getParentLimits());
    }

    private onCurrentAuxDataChanged(data: Record<string, unknown>): void {
        const current = this.getCurrentAuxData();
        if (!isEqual(current, data)) {
            this.currentConfig.update((config) => {
                const asBytes = new TextEncoder().encode(JSON.stringify(data));
                config.other = asBytes;
                return config;
            });
        }
    }

    private ensureNewNameUnique(name: string): string {
        const idx = this.configs.findIndex((e) => e.config.name === name);
        if (idx !== -1) {
            return name + (new Date().getTime() / 1000).toFixed(0);
        } else {
            return name;
        }
    }

    private updateCurrentConfig(): void {
        this.currentConfig.set(this.getCurrentConfig());
        this.currentAuxData.set(this.getCurrentAuxData());
        this.parentLimitsSetter?.(this.getParentLimits());
    }

    private updateConfigList(): void {
        this.configListSetter?.(this.getConfigList());
    }

    /// Returns a copy of the currently selected config.
    private getCurrentConfig(): DeckConfig.DeckConfig.Config {
        return cloneDeep(this.configs[this.selectedIdx].config.config!);
    }

    /// Extra data associated with current config (for add-ons)
    private getCurrentAuxData(): Record<string, unknown> {
        const conf = this.configs[this.selectedIdx].config.config!;
        return bytesToObject(conf.other);
    }

    private sortConfigs() {
        const currentConfigName = this.configs[this.selectedIdx].config.name;
        this.configs.sort((a, b) => localeCompare(a.config.name, b.config.name, { sensitivity: "base" }));
        this.selectedIdx = this.configs.findIndex(
            (c) => c.config.name == currentConfigName,
        );
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
        return list;
    }

    private getParentLimits(): ParentLimits {
        const parentConfigs = this.configs.filter((c) => this.currentDeck.parentConfigIds.includes(c.config.id));
        const newCards = parentConfigs.reduce(
            (previous, current) => Math.min(previous, current.config.config?.newPerDay ?? 0),
            2 ** 31,
        );
        const reviews = parentConfigs.reduce(
            (previous, current) => Math.min(previous, current.config.config?.reviewsPerDay ?? 0),
            2 ** 31,
        );
        return {
            newCards,
            reviews,
        };
    }
}

function bytesToObject(bytes: Uint8Array): Record<string, unknown> {
    if (!bytes.length) {
        return {};
    }

    let obj: Record<string, unknown>;

    try {
        obj = JSON.parse(new TextDecoder().decode(bytes));
    } catch (err) {
        console.log(`invalid json in deck config`);
        return {};
    }

    if (obj.constructor !== Object) {
        console.log(`invalid object in deck config`);
        return {};
    }

    return obj;
}

export function createLimits(): DeckConfig.DeckConfigsForUpdate.CurrentDeck.Limits {
    return DeckConfig.DeckConfigsForUpdate.CurrentDeck.Limits.create({});
}

export class ValueTab {
    readonly title: string;
    value: number | null;
    private setter: (value: number | null) => void;
    private disabledValue: number | null;
    private startValue: number | null;
    private initialValue: number | null;

    constructor(
        title: string,
        value: number | null,
        setter: (value: number | null) => void,
        disabledValue: number | null,
        startValue: number | null,
    ) {
        this.title = title;
        this.value = this.initialValue = value;
        this.setter = setter;
        this.disabledValue = disabledValue;
        this.startValue = startValue;
    }

    reset(): void {
        this.setter(this.initialValue);
    }

    disable(): void {
        this.setter(this.disabledValue);
    }

    enable(fallbackValue: number): void {
        this.value = this.value ?? this.startValue ?? fallbackValue;
        this.setter(this.value);
    }

    setValue(value: number): void {
        this.value = value;
        this.setter(value);
    }
}
