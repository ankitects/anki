<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Label from "./Label.svelte";
    import RevertButton from "./RevertButton.svelte";
    import Switch from "./Switch.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";

    export let value: boolean;
    export let defaultValue: boolean;
    export let markdownTooltip: string | undefined = undefined;

    const id = Math.random().toString(36).substring(2);
</script>

<Row --cols={6}>
    <Col --col-size={4}
        >{#if markdownTooltip}<TooltipLabel for={id} {markdownTooltip}
                ><slot /></TooltipLabel
            >{:else}<Label for={id}><slot /></Label>{/if}</Col
    >
    <Col --col-justify="flex-end">
        <Switch {id} bind:value />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
