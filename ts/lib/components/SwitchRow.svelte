<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "./Col.svelte";
    import ConfigInput from "./ConfigInput.svelte";
    import Label from "./Label.svelte";
    import RevertButton from "./RevertButton.svelte";
    import Row from "./Row.svelte";
    import Switch from "./Switch.svelte";

    export let value: boolean;
    export let defaultValue: boolean;
    export let disabled: boolean = false;
    export let hideRevert: boolean = false;

    const id = Math.random().toString(36).substring(2);
</script>

<Row --cols={6}>
    <Col --col-size={4}><Label for={id} preventMouseClick><slot /></Label></Col>
    <Col --col-justify="flex-end">
        <ConfigInput grow={false}>
            <Switch {id} bind:value {disabled} />
            {#if !hideRevert}
                <RevertButton slot="revert" bind:value {defaultValue} />
            {/if}
        </ConfigInput>
    </Col>
</Row>
