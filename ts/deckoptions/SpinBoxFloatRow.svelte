<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import marked from "marked";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import WithTooltip from "./WithTooltip.svelte";
    import Label from "./Label.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: any;
    export let defaultValue: any;
    export let min = 0;
    export let max: number | undefined = undefined;
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
    <Col size={5}>
        <RevertButton bind:value {defaultValue} />
        <SpinBoxFloat bind:value {min} {max} />
    </Col>
</Row>
