<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportAnkiPackageOptions } from "@generated/anki/import_export_pb";
    import { importAnkiPackage } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import HelpModal from "$lib/components/HelpModal.svelte";
    import Row from "$lib/components/Row.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { HelpItem } from "$lib/components/types";

    import ImportPage from "../import-page/ImportPage.svelte";
    import { updateChoices } from "./choices";

    export let path: string;
    export let options: ImportAnkiPackageOptions;

    const settings = {
        withScheduling: {
            title: tr.importingAlsoImportProgress(),
            help: tr.importingIncludeReviewsHelp(),
            url: HelpPage.PackageImporting.scheduling,
        },
        withDeckConfigs: {
            title: tr.importingWithDeckConfigs(),
            help: tr.importingWithDeckConfigsHelp(),
            url: HelpPage.PackageImporting.scheduling,
        },
        mergeNotetypes: {
            title: tr.importingMergeNotetypes(),
            help: tr.importingMergeNotetypesHelp(),
            url: HelpPage.PackageImporting.updating,
        },
        updateNotes: {
            title: tr.importingUpdateNotes(),
            help: tr.importingUpdateNotesHelp(),
            url: HelpPage.PackageImporting.updating,
        },
        updateNotetypes: {
            title: tr.importingUpdateNotetypes(),
            help: tr.importingUpdateNotetypesHelp(),
            url: HelpPage.PackageImporting.updating,
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

<ImportPage
    {path}
    importer={{
        doImport: () =>
            importAnkiPackage({ packagePath: path, options }, { alertOnError: false }),
    }}
>
    <Row class="d-block">
        <TitledContainer title={tr.importingImportOptions()}>
            <HelpModal
                title={tr.importingImportOptions()}
                url={HelpPage.PackageImporting.root}
                slot="tooltip"
                {helpSections}
                on:mount={(e) => {
                    modal = e.detail.modal;
                    carousel = e.detail.carousel;
                }}
            />

            <SwitchRow bind:value={options.withScheduling} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("withScheduling"))}
                >
                    {settings.withScheduling.title}
                </SettingTitle>
            </SwitchRow>

            <SwitchRow bind:value={options.withDeckConfigs} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("withDeckConfigs"))}
                >
                    {settings.withDeckConfigs.title}
                </SettingTitle>
            </SwitchRow>

            <details>
                <summary>{tr.importingUpdates()}</summary>
                <SwitchRow bind:value={options.mergeNotetypes} defaultValue={false}>
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("mergeNotetypes"),
                            )}
                    >
                        {settings.mergeNotetypes.title}
                    </SettingTitle>
                </SwitchRow>

                <EnumSelectorRow
                    bind:value={options.updateNotes}
                    defaultValue={0}
                    choices={updateChoices()}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(Object.keys(settings).indexOf("updateNotes"))}
                    >
                        {settings.updateNotes.title}
                    </SettingTitle>
                </EnumSelectorRow>

                <EnumSelectorRow
                    bind:value={options.updateNotetypes}
                    defaultValue={0}
                    choices={updateChoices()}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("updateNotetypes"),
                            )}
                    >
                        {settings.updateNotetypes.title}
                    </SettingTitle>
                </EnumSelectorRow>
            </details>
        </TitledContainer>
    </Row>
</ImportPage>
