<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "./Col.svelte";
    import ConfigInput from "./ConfigInput.svelte";
    import EnumSelector, { type Choice } from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";
    import Row from "./Row.svelte";
    import type { Breakpoint } from "./types";

    type T = $$Generic;

    export let value: T;
    export let defaultValue: T;
    export let breakpoint: Breakpoint = "md";
    export let choices: Choice<T>[];
    export let disabled: boolean = false;
    export let disabledChoices: T[] = [];
    export let hideRevert: boolean = false;
</script>

<Row --cols={13}>
    <Col --col-size={7} {breakpoint}>
        <slot />
    </Col>
    <Col --col-size={6} {breakpoint}>
        <ConfigInput>
            <EnumSelector bind:value {choices} {disabled} {disabledChoices} />
            {#if !hideRevert}
                <RevertButton slot="revert" bind:value {defaultValue} />
            {/if}
        </ConfigInput>
    </Col>
</Row>
