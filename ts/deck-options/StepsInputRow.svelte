<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import ConfigInput from "./ConfigInput.svelte";
    import RevertButton from "./RevertButton.svelte";
    import StepsInput from "./StepsInput.svelte";

    export let value: any;
    export let defaultValue: any;
</script>

<Row --cols={13}>
    <Col --col-size={7} breakpoint="xs">
        <slot />
    </Col>
    <Col --col-size={6} breakpoint="xs">
        <ConfigInput>
            <StepsInput bind:value />
            <RevertButton slot="revert" bind:value {defaultValue} />
        </ConfigInput>
    </Col>
</Row>
