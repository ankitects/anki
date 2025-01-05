// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { PlainMessage } from "@bufbuild/protobuf";
import type {
    DeckConfigsForUpdate,
    DeckConfigsForUpdate_CurrentDeck,
    UpdateDeckConfigsMode,
    UpdateDeckConfigsRequest,
} from "@generated/anki/deck_config_pb";
import { DeckConfig, DeckConfig_Config, DeckConfigsForUpdate_CurrentDeck_Limits } from "@generated/anki/deck_config_pb";
import { updateDeckConfigs } from "@generated/backend";
import { localeCompare } from "@tslib/i18n";
import { promiseWithResolver } from "@tslib/promise";
import { cloneDeep, isEqual } from "lodash-es";
import { tick } from "svelte";
import type { Readable, Writable } from "svelte/store";
import { get, readable, writable } from "svelte/store";

import type { DynamicSvelteComponent } from "$lib/sveltelib/dynamicComponent";

export type DeckOptionsId = bigint;

export interface ConfigWithCount {
    config: DeckConfig;
    useCount: number;
}

/** Info for showing the top selector */
export interface ConfigListEntry {
    idx: number;
    name: string;
    useCount: number;
    current: boolean;
}

type AllConfigs =
    & Required<
        Pick<
            PlainMessage<UpdateDeckConfigsRequest>,
            | "configs"
            | "cardStateCustomizer"
            | "limits"
            | "newCardsIgnoreReviewLimit"
            | "applyAllParentLimits"
            | "fsrs"
            | "fsrsReschedule"
        >
    >
    & { currentConfig: DeckConfig_Config };

export class DeckOptionsState {
    readonly currentConfig: Writable<DeckConfig_Config>;
    readonly currentAuxData: Writable<Record<string, unknown>>;
    readonly configList: Readable<ConfigListEntry[]>;
    readonly cardStateCustomizer: Writable<string>;
    readonly currentDeck: DeckConfigsForUpdate_CurrentDeck;
    readonly deckLimits: Writable<DeckConfigsForUpdate_CurrentDeck_Limits>;
    readonly defaults: DeckConfig_Config;
    readonly addonComponents: Writable<DynamicSvelteComponent[]>;
    readonly newCardsIgnoreReviewLimit: Writable<boolean>;
    readonly applyAllParentLimits: Writable<boolean>;
    readonly fsrs: Writable<boolean>;
    readonly fsrsReschedule: Writable<boolean> = writable(false);
    readonly daysSinceLastOptimization: Writable<number>;
    readonly currentPresetName: Writable<string>;
    /** Used to detect if there are any pending changes */
    readonly originalConfigsPromise: Promise<AllConfigs>;
    readonly originalConfigsResolve: (value: AllConfigs) => void;

    private targetDeckId: DeckOptionsId;
    private configs: ConfigWithCount[];
    private selectedIdx: number;
    private configListSetter!: (val: ConfigListEntry[]) => void;
    private modifiedConfigs: Set<DeckOptionsId> = new Set();
    private removedConfigs: DeckOptionsId[] = [];
    private schemaModified: boolean;
    private _presetAssignmentsChanged = false;
    // tracks presets that have already been
    // selected/loaded once, via their ids.
    // needed for proper change detection
    private loadedPresets: Set<DeckConfig["id"]> = new Set();

    constructor(targetDeckId: DeckOptionsId, data: DeckConfigsForUpdate) {
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
        this.cardStateCustomizer = writable(data.cardStateCustomizer);
        this.deckLimits = writable(data.currentDeck?.limits ?? createLimits());
        this.newCardsIgnoreReviewLimit = writable(data.newCardsIgnoreReviewLimit);
        this.applyAllParentLimits = writable(data.applyAllParentLimits);
        this.fsrs = writable(data.fsrs);
        this.daysSinceLastOptimization = writable(data.daysSinceLastFsrsOptimize);

        // decrement the use count of the starting item, as we'll apply +1 to currently
        // selected one at display time
        this.configs[this.selectedIdx].useCount -= 1;
        this.currentConfig = writable(this.getCurrentConfig());
        this.currentAuxData = writable(this.getCurrentAuxData());
        this.currentPresetName = writable(this.configs[this.selectedIdx].config.name);
        this.configList = readable(this.getConfigList(), (set) => {
            this.configListSetter = set;
            return;
        });
        this.schemaModified = data.schemaModified;
        this.addonComponents = writable([]);

        // create a temporary subscription to force our setters to be set immediately,
        // so unit tests don't get stale results
        get(this.configList);

        // update our state when the current config is changed
        this.currentConfig.subscribe((val) => this.onCurrentConfigChanged(val));
        this.currentAuxData.subscribe((val) => this.onCurrentAuxDataChanged(val));

        // no need to call markCurrentPresetAsLoaded here
        // since any changes components make will be before
        // originalConfigsResolve is called on mount
        this.loadedPresets.add(this.configs[this.selectedIdx].config.id);

        // Must be resolved after all components are mounted, as some components
        // may modify the config during their initialization.
        [this.originalConfigsPromise, this.originalConfigsResolve] = promiseWithResolver<AllConfigs>();
    }

    /**
     * Patch the original config if components change it after preset select
     * [EasyDays] and [DateInput] both do this in certain cases
     * [SpinBox] and [ParamsInput] also apply their own roundings
     * We only need to patch when the preset is first selected.
     */
    async markCurrentPresetAsLoaded(): Promise<void> {
        const id = this.configs[this.selectedIdx].config.id;
        const loaded = this.loadedPresets;
        // ignore new presets with an id of 0
        if (id && !loaded.has(id)) {
            // preset was loaded for the first time, patch original config
            loaded.add(id);
            const original = await this.originalConfigsPromise;
            // can't index into `original` with `this.selectedIdx` due to `sortConfigs`
            const idx = original.configs.findIndex((conf) => conf.id === id);
            // this should never be -1, since new presets are excluded, and removed presets aren't considered
            if (idx !== -1) {
                original.configs[idx] = cloneDeep(this.configs[this.selectedIdx].config);
            }
        }
    }

    setCurrentIndex(index: number): void {
        this.selectedIdx = index;
        this._presetAssignmentsChanged = true;
        this.updateCurrentConfig();
        // use counts have changed
        this.updateConfigList();
        // wait for components that modify config on preset select
        tick().then(() => this.markCurrentPresetAsLoaded());
    }

    getCurrentName(): string {
        return this.configs[this.selectedIdx].config.name;
    }

    getCurrentNameForSearch(): string {
        return this.getCurrentName().replace(/([\\"])/g, "\\$1");
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

    /** Adds a new config, making it current. */
    addConfig(name: string): void {
        this.addConfigFrom(name, this.defaults);
    }

    /** Clone the current config, making it current. */
    cloneConfig(name: string): void {
        this.addConfigFrom(name, this.configs[this.selectedIdx].config.config!);
    }

    /** Clone the current config, making it current. */
    private addConfigFrom(name: string, source: DeckConfig_Config): void {
        const uniqueName = this.ensureNewNameUnique(name);
        const config = new DeckConfig({
            id: 0n,
            name: uniqueName,
            config: new DeckConfig_Config(cloneDeep(source)),
        });
        const configWithCount = { config, useCount: 0 };
        this.configs.push(configWithCount);
        this.selectedIdx = this.configs.length - 1;
        this._presetAssignmentsChanged = true;
        this.sortConfigs();
        this.updateCurrentConfig();
        this.updateConfigList();
    }

    removalWilLForceFullSync(): boolean {
        return !this.schemaModified && this.configs[this.selectedIdx].config.id !== 0n;
    }

    defaultConfigSelected(): boolean {
        return this.configs[this.selectedIdx].config.id === 1n;
    }

    /** Will throw if the default deck is selected. */
    removeCurrentConfig(): void {
        const currentId = this.configs[this.selectedIdx].config.id;
        if (currentId === 1n) {
            throw Error("can't remove default config");
        }
        if (currentId !== 0n) {
            this.removedConfigs.push(currentId);
            this.schemaModified = true;
        }
        this.configs.splice(this.selectedIdx, 1);
        const newIdx = Math.max(0, this.selectedIdx - 1);
        this.setCurrentIndex(newIdx);
    }

    dataForSaving(
        mode: UpdateDeckConfigsMode,
    ): PlainMessage<UpdateDeckConfigsRequest> {
        const modifiedConfigsExcludingCurrent = this.configs
            .map((c) => c.config)
            .filter((c, idx) => {
                return (
                    idx !== this.selectedIdx
                    && (c.id === 0n || this.modifiedConfigs.has(c.id))
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
            mode,
            cardStateCustomizer: get(this.cardStateCustomizer),
            limits: get(this.deckLimits),
            newCardsIgnoreReviewLimit: get(this.newCardsIgnoreReviewLimit),
            applyAllParentLimits: get(this.applyAllParentLimits),
            fsrs: get(this.fsrs),
            fsrsReschedule: get(this.fsrsReschedule),
        };
    }

    presetAssignmentsChanged(): boolean {
        return this._presetAssignmentsChanged;
    }

    async save(mode: UpdateDeckConfigsMode): Promise<void> {
        await updateDeckConfigs(
            this.dataForSaving(mode),
        );
    }

    private onCurrentConfigChanged(config: DeckConfig_Config): void {
        const configOuter = this.configs[this.selectedIdx].config;
        if (!isEqual(config, configOuter.config)) {
            configOuter.config = config;
            if (configOuter.id) {
                this.modifiedConfigs.add(configOuter.id);
            }
        }
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
    }

    private updateConfigList(): void {
        this.configListSetter?.(this.getConfigList());
        this.currentPresetName.set(this.configs[this.selectedIdx].config.name);
    }

    /** Returns a copy of the currently selected config. */
    private getCurrentConfig(): DeckConfig_Config {
        return cloneDeep(this.configs[this.selectedIdx].config.config!);
    }

    /** Extra data associated with current config (for add-ons) */
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

    private getAllConfigs(): AllConfigs {
        return cloneDeep({
            configs: this.configs.map(c => c.config),
            cardStateCustomizer: get(this.cardStateCustomizer),
            limits: get(this.deckLimits),
            newCardsIgnoreReviewLimit: get(this.newCardsIgnoreReviewLimit),
            applyAllParentLimits: get(this.applyAllParentLimits),
            fsrs: get(this.fsrs),
            fsrsReschedule: get(this.fsrsReschedule),
            currentConfig: get(this.currentConfig),
        });
    }

    async isModified(): Promise<boolean> {
        const original = await this.originalConfigsPromise;
        const current = this.getAllConfigs();
        return !isEqual(original, current);
    }

    resolveOriginalConfigs(): void {
        this.originalConfigsResolve(this.getAllConfigs());
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

export function createLimits(): DeckConfigsForUpdate_CurrentDeck_Limits {
    return new DeckConfigsForUpdate_CurrentDeck_Limits({});
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

/** Ensure blur handler has fired so changes get committed. */
export async function commitEditing(): Promise<void> {
    if (document.activeElement instanceof HTMLElement) {
        document.activeElement.blur();
    }
    await tick();
}

export function fsrsParams(config: DeckConfig_Config): number[] {
    if (config.fsrsParams5) {
        return config.fsrsParams5;
    } else {
        return config.fsrsParams4;
    }
}
