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
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { HelpItem } from "$lib/components/types";

    import { delimiterChoices } from "./choices";
    import type { ImportCsvState } from "./lib";
    import Preview from "./Preview.svelte";

    export let state: ImportCsvState;

    const metadata = state.metadata;

    const settings = {
        delimiter: {
            title: tr.importingFieldSeparator(),
            help: tr.importingFieldSeparatorHelp(),
            url: HelpPage.TextImporting.root,
        },
        isHtml: {
            title: tr.importingAllowHtmlInFields(),
            help: tr.importingAllowHtmlInFieldsHelp(),
            url: HelpPage.TextImporting.html,
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

<TitledContainer title={tr.importingFile()}>
    <HelpModal
        title={tr.importingFile()}
        url={HelpPage.TextImporting.root}
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <EnumSelectorRow
        bind:value={$metadata.delimiter}
        defaultValue={state.defaultDelimiter}
        choices={delimiterChoices()}
        disabled={$metadata.forceDelimiter}
    >
        <SettingTitle
            on:click={() => openHelpModal(Object.keys(settings).indexOf("delimiter"))}
        >
            {settings.delimiter.title}
        </SettingTitle>
    </EnumSelectorRow>

    <SwitchRow
        bind:value={$metadata.isHtml}
        defaultValue={state.defaultIsHtml}
        disabled={$metadata.forceIsHtml}
    >
        <SettingTitle
            on:click={() => openHelpModal(Object.keys(settings).indexOf("isHtml"))}
        >
            {settings.isHtml.title}
        </SettingTitle>
    </SwitchRow>

    <Preview {state} />
</TitledContainer>
