<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@tslib/anki/import_export_pb";
    import { importAnkiPackage } from "@tslib/anki/import_export_service";
    import { importDone } from "@tslib/backend";
    import * as tr from "@tslib/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";
    import BackendProgressIndicator from "components/BackendProgressIndicator.svelte";

    import Col from "../components/Col.svelte";
    import HelpModal from "../components/HelpModal.svelte";
    import Row from "../components/Row.svelte";
    import SettingTitle from "../components/SettingTitle.svelte";
    import StickyHeader from "../components/StickyHeader.svelte";
    import Switch from "../components/Switch.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import type { HelpItem } from "../components/types";
    import ImportLogPage from "../import-log/ImportLogPage.svelte";

    export let path: string;
    export let mergeNotetypes: boolean = false;

    let importResponse: ImportResponse | undefined = undefined;
    let importing = false;
    const settings = {
        mergeNotetypes: {
            title: tr.importingMergeNotetypes(),
            help: "placeholder",
            url: HelpPage.Importing.ankiPackage,
        },
    };
    const helpSections = Object.values(settings) as HelpItem[];
    let modal: Modal;
    let carousel: Carousel;

    async function onImport(): Promise<ImportResponse> {
        const result = await importAnkiPackage({
            packagePath: path,
            mergeNotetypes,
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

    <TitledContainer title={tr.importingImportOptions()}>
        <HelpModal
            title={tr.importingImportOptions()}
            url={HelpPage.Importing.ankiPackage}
            {helpSections}
            on:mount={(e) => {
                modal = e.detail.modal;
                carousel = e.detail.carousel;
            }}
        />

        <Row --cols={2}>
            <Col --col-size={1}>
                {tr.importingMergeNotetypes()}
            </Col>
            <Col --col-size={1} --col-justify="flex-end">
                <Switch id={undefined} bind:value={mergeNotetypes} />
            </Col>
            <SettingTitle
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("newLimit"))}
            >
                {settings.mergeNotetypes.title}
            </SettingTitle>
        </Row>
    </TitledContainer>
{/if}

<style lang="scss">
    :global(.anki-package-page) {
        --gutter-inline: 0.25rem;

        :global(.row) {
            // rows have negative margins by default
            --bs-gutter-x: 0;
            margin-bottom: 0.5rem;
        }
    }
</style>
