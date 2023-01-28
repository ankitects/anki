<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import SpinBox from "../components/SpinBox.svelte";
    import ConfigInput from "./ConfigInput.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: number;
    export let defaultValue: number;
    export let min = 0;
    export let max = 9999;
</script>

<Row --cols={13}>
    <Col --col-size={7} breakpoint="xs">
        <slot />
    </Col>
    <Col --col-size={6} breakpoint="xs">
        <ConfigInput>
            <SpinBox bind:value {min} {max} step={0.01} />
            <RevertButton slot="revert" bind:value {defaultValue} />
        </ConfigInput>
    </Col>
</Row>
