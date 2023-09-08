<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        ImportAnkiPackageOptions,
        ImportResponse,
    } from "@tslib/anki/import_export_pb";
    import { importAnkiPackage } from "@tslib/backend";
    import { importDone } from "@tslib/backend";
    import * as tr from "@tslib/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";
    import BackendProgressIndicator from "components/BackendProgressIndicator.svelte";
    import Container from "components/Container.svelte";
    import EnumSelectorRow from "components/EnumSelectorRow.svelte";
    import Row from "components/Row.svelte";

    import HelpModal from "../components/HelpModal.svelte";
    import SettingTitle from "../components/SettingTitle.svelte";
    import StickyHeader from "../components/StickyHeader.svelte";
    import SwitchRow from "../components/SwitchRow.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import type { HelpItem } from "../components/types";
    import ImportLogPage from "../import-log/ImportLogPage.svelte";

    export let path: string;
    export let options: ImportAnkiPackageOptions;

    let importResponse: ImportResponse | undefined = undefined;
    let importing = false;

    const updateChoices = [
        tr.importingUpdateIfNewer(),
        tr.importingUpdateAlways(),
        tr.importingUpdateNever(),
    ];

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

    async function onImport(): Promise<ImportResponse> {
        const result = await importAnkiPackage({
            packagePath: path,
            options,
        });
        await importDone({});
        importing = false;
        return result;
    }

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

{#if importing}
    <BackendProgressIndicator task={onImport} bind:result={importResponse} />
{:else if importResponse}
    <ImportLogPage response={importResponse} params={{ path }} />
{:else}
    <StickyHeader {path} onImport={() => (importing = true)} />

    <Container
        breakpoint="sm"
        --gutter-inline="0.25rem"
        --gutter-block="0.75rem"
        class="container-columns"
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
                    choices={updateChoices}
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
                    choices={updateChoices}
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

                <SwitchRow bind:value={options.withScheduling} defaultValue={false}>
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("withScheduling"),
                            )}
                    >
                        {settings.withScheduling.title}
                    </SettingTitle>
                </SwitchRow>
            </TitledContainer>
        </Row>
    </Container>
{/if}

<style lang="scss">
    :global(.row) {
        // rows have negative margins by default
        --bs-gutter-x: 0;
        margin-bottom: 0.5rem;
    }
</style>
