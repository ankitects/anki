<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Breakpoint } from "./col";

    import marked from "marked";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import WithTooltip from "./WithTooltip.svelte";
    import Label from "./Label.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: number;
    export let defaultValue: number;
    export let breakpoint: Breakpoint = "md";
    export let choices: string[];
    export let markdownTooltip: string;
</script>

<Row>
    <Col size={7}>
        <WithTooltip tooltip={marked(markdownTooltip)} let:createTooltip>
            <Label on:mount={(event) => createTooltip(event.detail.span)}
                ><slot /></Label
            >
        </WithTooltip>
    </Col>
    <Col {breakpoint} size={5}>
        <RevertButton bind:value {defaultValue} />
        <EnumSelector bind:value {choices} />
    </Col>
</Row>
