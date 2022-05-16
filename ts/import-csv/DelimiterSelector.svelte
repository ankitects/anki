<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import SelectButton from "../components/SelectButton.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import * as tr from "../lib/ftl";
    import { ImportExport } from "../lib/proto";

    export let delimiter: ImportExport.CsvMetadata.Delimiter;

    const delimiters = delimiterNames();

    function updateCurrentDelimiter(event: Event) {
        const index = parseInt((event.target! as HTMLSelectElement).value);
        delimiter = delimiters[index][0];
    }

    function delimiterNames(): [ImportExport.CsvMetadata.Delimiter, string][] {
        const Delimiter = ImportExport.CsvMetadata.Delimiter;
        return [
            [Delimiter.TAB, tr.importingTab()],
            [Delimiter.PIPE, tr.importingPipe()],
            [Delimiter.SEMICOLON, tr.importingSemicolon()],
            [Delimiter.COLON, tr.importingColon()],
            [Delimiter.COMMA, tr.importingComma()],
            [Delimiter.SPACE, tr.studyingSpace()],
        ];
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        <div>{tr.importingFieldDelimiter()}</div>
    </Col>
    <Col --col-size={1}>
        <ButtonGroup class="flex-grow-1">
            <SelectButton class="flex-grow-1" on:change={updateCurrentDelimiter}>
                {#each delimiters as delimiterName, idx}
                    <SelectOption
                        value={String(idx)}
                        selected={delimiterName[0] === delimiter}
                    >
                        {delimiterName[1]}
                    </SelectOption>
                {/each}
            </SelectButton>
        </ButtonGroup>
    </Col>
</Row>
