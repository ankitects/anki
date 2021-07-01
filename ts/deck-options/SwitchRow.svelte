<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import Label from "./Label.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";
    import Switch from "./Switch.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: boolean;
    export let defaultValue: boolean;
    export let markdownTooltip: string | undefined = undefined;

    const id = Math.random().toString(36).substring(2);
</script>

<Row>
    <Col>
        {#if markdownTooltip}<TooltipLabel for={id} {markdownTooltip}
                ><slot /></TooltipLabel
            >{:else}<Label for={id}><slot /></Label>{/if}
    </Col>
    <Col grow={false}>
        <Switch {id} bind:value />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
