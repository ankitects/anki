<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import type { Breakpoint } from "../components/types";
    import ConfigInput from "./ConfigInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: number;
    export let defaultValue: number;
    export let breakpoint: Breakpoint = "md";
    export let choices: string[];
    export let disabled: number[] = [];
</script>

<Row --cols={13}>
    <Col --col-size={7} {breakpoint}>
        <slot />
    </Col>
    <Col --col-size={6} {breakpoint}>
        <ConfigInput>
            <EnumSelector bind:value options={choices} {disabled} />
            <RevertButton slot="revert" bind:value {defaultValue} />
        </ConfigInput>
    </Col>
</Row>
