<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import type { NumberValueEvent } from "./events";

    export let label: string;
    export let tooltip: string;
    export let value: number;
    export let defaultValue: number;
    export let min = 1;
    export let max = 9999;

    const dispatch = createEventDispatcher();

    let stringValue: string;
    $: stringValue = value.toFixed(2);

    function update(this: HTMLInputElement): void {
        dispatch("changed", {
            value: Math.min(max, Math.max(min, parseFloat(this.value))),
        });
    }

    function revert(evt: NumberValueEvent): void {
        dispatch("changed", { value: evt.detail.value });
    }
</script>

<ConfigEntry {label} {tooltip} {value} {defaultValue} on:revert={revert}>
    <input
        type="number"
        {min}
        {max}
        step="0.01"
        value={stringValue}
        on:blur={update}
        class="form-control"
    />
</ConfigEntry>
