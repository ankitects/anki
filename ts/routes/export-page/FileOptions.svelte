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

    import { getExporters } from "./lib";
    import type { Exporter, ExportOptions } from "./types";

    export let withLimit: boolean = false;
    export let exporter: Exporter;
    export let exportOptions: ExportOptions;

    const defaultLegacySupport = exportOptions.legacySupport;

    const exporters = getExporters(withLimit);

    const exporterChoices = exporters.map((exp, idx) => ({
        value: idx,
        label: `${exp.label} (.${exp.extension})`,
    }));
    const defaultExporterIdx = exporters.findIndex((exp) => exp.isDefault);
    let exporterIdx = defaultExporterIdx;
    $: exporter = exporters[exporterIdx];

    const settings = {
        format: {
            title: tr.exportingFormat(),
            help: tr.exportingFormatHelp(),
            url: HelpPage.Exporting.root,
        },
        legacySupport: {
            title: tr.exportingSupportOlderAnkiVersions(),
            help: tr.exportingSupportOlderAnkiVersionsHelp(),
            url: HelpPage.Exporting.packagedDecks,
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

<TitledContainer title={tr.importingFile()}>
    <HelpModal
        title={tr.importingFile()}
        url={HelpPage.PackageImporting.root}
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <EnumSelectorRow
        bind:value={exporterIdx}
        defaultValue={defaultExporterIdx}
        choices={exporterChoices}
    >
        <SettingTitle
            on:click={() => openHelpModal(Object.keys(settings).indexOf("format"))}
        >
            {settings.format.title}
        </SettingTitle>
    </EnumSelectorRow>

    {#if exporter.showLegacySupport}
        <SwitchRow
            bind:value={exportOptions.legacySupport}
            defaultValue={defaultLegacySupport}
        >
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("legacySupport"))}
            >
                {settings.legacySupport.title}
            </SettingTitle>
        </SwitchRow>
    {/if}
</TitledContainer>
