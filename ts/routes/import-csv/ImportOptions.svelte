<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import HelpModal from "$lib/components/HelpModal.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { HelpItem } from "$lib/components/types";
    import TagsRow from "$lib/tag-editor/TagsRow.svelte";

    import { dupeResolutionChoices, matchScopeChoices } from "./choices";
    import type { ImportCsvState } from "./lib";

    export let state: ImportCsvState;

    const metadata = state.metadata;
    const globalNotetype = state.globalNotetype;
    const deckId = state.deckId;

    const settings = {
        notetype: {
            title: tr.notetypesNotetype(),
            help: tr.importingNotetypeHelp(),
            url: HelpPage.TextImporting.root,
        },
        deck: {
            title: tr.decksDeck(),
            help: tr.importingDeckHelp(),
            url: HelpPage.TextImporting.root,
        },
        dupeResolution: {
            title: tr.importingExistingNotes(),
            help: tr.importingExistingNotesHelp(),
            url: HelpPage.TextImporting.updating,
        },
        matchScope: {
            title: tr.importingMatchScope(),
            help: tr.importingMatchScopeHelp(),
            url: HelpPage.TextImporting.updating,
        },
        globalTags: {
            title: tr.importingTagAllNotes(),
            help: tr.importingTagAllNotesHelp(),
            url: HelpPage.TextImporting.root,
        },
        updatedTags: {
            title: tr.importingTagUpdatedNotes(),
            help: tr.importingTagUpdatedNotesHelp(),
            url: HelpPage.TextImporting.root,
        },
    };
    const helpSections: HelpItem[] = Object.values(settings);
    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

<TitledContainer title={tr.importingImportOptions()}>
    <HelpModal
        title={tr.importingImportOptions()}
        url={HelpPage.TextImporting.root}
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />

    {#if $globalNotetype !== null}
        <EnumSelectorRow
            bind:value={$globalNotetype.id}
            defaultValue={state.defaultNotetypeId}
            choices={state.notetypeNameIds.map(({ id, name }) => {
                return { label: name, value: id };
            })}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("notetype"))}
            >
                {settings.notetype.title}
            </SettingTitle>
        </EnumSelectorRow>
    {/if}

    {#if $deckId !== null}
        <EnumSelectorRow
            bind:value={$deckId}
            defaultValue={state.defaultDeckId}
            choices={state.deckNameIds.map(({ id, name }) => {
                return { label: name, value: id };
            })}
        >
            <SettingTitle
                on:click={() => openHelpModal(Object.keys(settings).indexOf("deck"))}
            >
                {settings.deck.title}
            </SettingTitle>
        </EnumSelectorRow>
    {/if}

    <EnumSelectorRow
        bind:value={$metadata.dupeResolution}
        defaultValue={0}
        choices={dupeResolutionChoices()}
    >
        <SettingTitle
            on:click={() =>
                openHelpModal(Object.keys(settings).indexOf("dupeResolution"))}
        >
            {settings.dupeResolution.title}
        </SettingTitle>
    </EnumSelectorRow>

    <EnumSelectorRow
        bind:value={$metadata.matchScope}
        defaultValue={0}
        choices={matchScopeChoices()}
    >
        <SettingTitle
            on:click={() => openHelpModal(Object.keys(settings).indexOf("matchScope"))}
        >
            {settings.matchScope.title}
        </SettingTitle>
    </EnumSelectorRow>

    <TagsRow bind:tags={$metadata.globalTags} keyCombination={"Control+T"}>
        <SettingTitle
            on:click={() => openHelpModal(Object.keys(settings).indexOf("globalTags"))}
        >
            {settings.globalTags.title}
        </SettingTitle>
    </TagsRow>

    <TagsRow bind:tags={$metadata.updatedTags} keyCombination={"Control+Shift+T"}>
        <SettingTitle
            on:click={() => openHelpModal(Object.keys(settings).indexOf("updatedTags"))}
        >
            {settings.updatedTags.title}
        </SettingTitle>
    </TagsRow>
</TitledContainer>
