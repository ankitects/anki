<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DeckNameId } from "@generated/anki/decks_pb";
    import * as tr from "@generated/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import HelpModal from "$lib/components/HelpModal.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { HelpItem } from "$lib/components/types";

    import type { Exporter, ExportOptions, LimitChoice, LimitValue } from "./types";

    const defaultExportOptions: ExportOptions = {
        includeScheduling: false,
        includeDeckConfigs: false,
        includeMedia: true,
        includeTags: true,
        includeHtml: true,
        legacySupport: false,
        includeDeck: true,
        includeNotetype: true,
        includeGuid: false,
    };

    export let deckId: bigint | null;
    export let noteIds: bigint[];
    export let deckNames: DeckNameId[];
    export let exporter: Exporter;
    export let limit: LimitValue;
    export let limitLabel: string;
    export let exportOptions: ExportOptions = { ...defaultExportOptions };

    const limitChoices: LimitChoice[] =
        noteIds.length === 0
            ? [
                  {
                      label: tr.exportingAllDecks(),
                      value: 0,
                      limit: null,
                  },
                  ...deckNames.map((d, idx) => ({
                      label: d.name,
                      value: idx + 1,
                      limit: d.id,
                  })),
              ]
            : [
                  {
                      label: tr.exportingSelectedNotes(),
                      value: 0,
                      limit: noteIds,
                  },
              ];
    const defaultLimitIdx =
        deckId === null ? 0 : limitChoices.findIndex((l) => l.limit === deckId) ?? 0;
    let limitIdx = defaultLimitIdx;
    $: limit = limitChoices[limitIdx].limit;
    $: limitLabel = limitChoices[limitIdx].label;

    const settings = {
        includeScheduling: {
            title: tr.exportingIncludeSchedulingInformation(),
            help: tr.exportingIncludeSchedulingInformationHelp(),
            url: HelpPage.Exporting.packagedDecks,
        },
        includeDeckConfigs: {
            title: tr.exportingIncludeDeckConfigs(),
            help: tr.exportingIncludeDeckConfigsHelp(),
            url: HelpPage.Exporting.packagedDecks,
        },
        includeMedia: {
            title: tr.exportingIncludeMedia(),
            help: tr.exportingIncludeMediaHelp(),
            url: HelpPage.Exporting.root,
        },
        includeTags: {
            title: tr.exportingIncludeTags(),
            help: tr.exportingIncludeTagsHelp(),
            url: HelpPage.Exporting.textFiles,
        },
        includeHtml: {
            title: tr.exportingIncludeHtmlAndMediaReferences(),
            help: tr.exportingIncludeHtmlAndMediaReferencesHelp(),
            url: HelpPage.TextImporting.html,
        },
        includeDeck: {
            title: tr.exportingIncludeDeck(),
            help: tr.exportingIncludeDeckHelp(),
            url: HelpPage.TextImporting.deckColumn,
        },
        includeNotetype: {
            title: tr.exportingIncludeNotetype(),
            help: tr.exportingIncludeNotetypeHelp(),
            url: HelpPage.TextImporting.notetypeColumn,
        },
        includeGuid: {
            title: tr.exportingIncludeGuid(),
            help: tr.exportingIncludeGuidHelp(),
            url: HelpPage.TextImporting.guidColumn,
        },
    };
    const helpSections = Object.values(settings) as HelpItem[];
    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

<TitledContainer title={tr.exportingContent()}>
    <HelpModal
        title={tr.exportingContent()}
        url={HelpPage.PackageImporting.root}
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />

    {#if exporter.showDeckList}
        <EnumSelectorRow
            bind:value={limitIdx}
            defaultValue={defaultLimitIdx}
            choices={limitChoices}
            disabled={limitChoices.length === 1}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeScheduling"))}
            >
                {tr.browsingNotes()}
            </SettingTitle>
        </EnumSelectorRow>
    {/if}

    {#if exporter.showIncludeScheduling}
        <SwitchRow
            bind:value={exportOptions.includeScheduling}
            defaultValue={defaultExportOptions.includeScheduling}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeScheduling"))}
            >
                {settings.includeScheduling.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeDeckConfigs}
        <SwitchRow
            bind:value={exportOptions.includeDeckConfigs}
            defaultValue={defaultExportOptions.includeDeckConfigs}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeDeckConfigs"))}
            >
                {settings.includeDeckConfigs.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeMedia}
        <SwitchRow
            bind:value={exportOptions.includeMedia}
            defaultValue={defaultExportOptions.includeMedia}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeMedia"))}
            >
                {settings.includeMedia.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeTags}
        <SwitchRow
            bind:value={exportOptions.includeTags}
            defaultValue={defaultExportOptions.includeTags}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeTags"))}
            >
                {settings.includeTags.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeHtml}
        <SwitchRow
            bind:value={exportOptions.includeHtml}
            defaultValue={defaultExportOptions.includeHtml}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeHtml"))}
            >
                {settings.includeHtml.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeDeck}
        <SwitchRow
            bind:value={exportOptions.includeDeck}
            defaultValue={defaultExportOptions.includeDeck}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeDeck"))}
            >
                {settings.includeDeck.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeNotetype}
        <SwitchRow
            bind:value={exportOptions.includeNotetype}
            defaultValue={defaultExportOptions.includeNotetype}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeNotetype"))}
            >
                {settings.includeNotetype.title}
            </SettingTitle>
        </SwitchRow>
    {/if}

    {#if exporter.showIncludeGuid}
        <SwitchRow
            bind:value={exportOptions.includeGuid}
            defaultValue={defaultExportOptions.includeGuid}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("includeGuid"))}
            >
                {settings.includeGuid.title}
            </SettingTitle>
        </SwitchRow>
    {/if}
</TitledContainer>
