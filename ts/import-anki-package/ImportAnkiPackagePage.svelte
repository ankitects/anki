<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportAnkiPackageOptions } from "@tslib/anki/import_export_pb";
    import { importAnkiPackage } from "@tslib/backend";
    import * as tr from "@tslib/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import EnumSelectorRow from "../components/EnumSelectorRow.svelte";
    import HelpModal from "../components/HelpModal.svelte";
    import ImportPage from "../components/ImportPage.svelte";
    import Row from "../components/Row.svelte";
    import SettingTitle from "../components/SettingTitle.svelte";
    import SwitchRow from "../components/SwitchRow.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import type { HelpItem } from "../components/types";
    import { updateChoices } from "./choices";

    export let path: string;
    export let options: ImportAnkiPackageOptions;

    const settings = {
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
        withScheduling: {
            title: tr.importingIncludeReviews(),
            help: tr.importingIncludeReviewsHelp(),
            url: HelpPage.PackageImporting.scheduling,
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

            <SwitchRow bind:value={options.mergeNotetypes} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("mergeNotetypes"))}
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
                        openHelpModal(Object.keys(settings).indexOf("updateNotetypes"))}
                >
                    {settings.updateNotetypes.title}
                </SettingTitle>
            </EnumSelectorRow>

            <SwitchRow bind:value={options.withScheduling} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("withScheduling"))}
                >
                    {settings.withScheduling.title}
                </SettingTitle>
            </SwitchRow>
        </TitledContainer>
    </Row>
</ImportPage>
