<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "$lib/components/Col.svelte";
    import ConfigInput from "$lib/components/ConfigInput.svelte";
    import RevertButton from "$lib/components/RevertButton.svelte";
    import Row from "$lib/components/Row.svelte";
    import SpinBox from "$lib/components/SpinBox.svelte";

    export let value: number;
    export let defaultValue: number;
    export let min = 0;
    export let max = 9999;
    export let step = 0.01;
    export let percentage = false;
    export let focused = false;
</script>

<Row --cols={13}>
    <Col --col-size={7} breakpoint="xs">
        <slot />
    </Col>
    <Col --col-size={6} breakpoint="xs">
        <ConfigInput>
            <SpinBox bind:value {min} {max} {step} {percentage} bind:focused />
            <RevertButton slot="revert" bind:value {defaultValue} />
        </ConfigInput>
    </Col>
</Row>
