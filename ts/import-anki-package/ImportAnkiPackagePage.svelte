<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@tslib/anki/import_export_pb";
    import { importAnkiPackage } from "@tslib/anki/import_export_service";
    import { importDone } from "@tslib/backend";
    import * as tr from "@tslib/ftl";
    import BackendProgressIndicator from "components/BackendProgressIndicator.svelte";
    import Switch from "components/Switch.svelte";

    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Spacer from "../components/Spacer.svelte";
    import StickyHeader from "../components/StickyHeader.svelte";
    import Switch from "../components/Switch.svelte";
    import ImportLogPage from "../import-log/ImportLogPage.svelte";
    import Header from "./Header.svelte";

    export let path: string;
    export let mergeNotetypes: boolean = false;

    let importResponse: ImportResponse | undefined = undefined;
    let importing = false;

    async function onImport(): Promise<ImportResponse> {
        const result = await importAnkiPackage({
            packagePath: path,
            mergeNotetypes,
        });
        await importDone({});
        importing = false;
        return result;
    }
</script>

{#if importing}
    <BackendProgressIndicator task={onImport} bind:result={importResponse} />
{:else if importResponse}
    <ImportLogPage response={importResponse} params={{ path }} />
{:else}
    <StickyHeader {path} onImport={() => (importing = true)} />

    <Container class="csv-page">
        <Row --cols={2}>
            <Col --col-size={1} breakpoint="md">
                <Container>
                    <Header heading={tr.importingImportOptions()} />
                    <Spacer --height="1.5rem" />
                    <Row --cols={2}>
                        <Col --col-size={1}>
                            {tr.importingMergeNotetypes()}
                        </Col>
                        <Col --col-size={1} --col-justify="flex-end">
                            <Switch id={undefined} bind:value={mergeNotetypes} />
                        </Col>
                    </Row>
                </Container>
            </Col>
        </Row>
    </Container>
{/if}

<style lang="scss">
    :global(.csv-page) {
        --gutter-inline: 0.25rem;

        :global(.row) {
            // rows have negative margins by default
            --bs-gutter-x: 0;
            margin-bottom: 0.5rem;
        }
    }
</style>
