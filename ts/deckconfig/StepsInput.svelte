<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { stepsToString, stringToSteps } from "./steps";
    import ConfigEntry from "./ConfigEntry.svelte";
    import type { NumberValueEvent } from "./events";

    export let label: string;
    export let subLabel: string;
    export let value: number[];
    export let defaultValue: number[];
    export let warnings: string[] = [];

    const dispatch = createEventDispatcher();

    let stringValue: string;
    $: stringValue = stepsToString(value);

    function update(this: HTMLInputElement): void {
        const value = stringToSteps(this.value);
        dispatch("changed", { value });
    }

    function revert(evt: NumberValueEvent): void {
        dispatch("changed", { value: evt.detail.value });
    }
</script>

<ConfigEntry {label} {subLabel} {value} {defaultValue} {warnings} on:revert={revert}>
    <input type="text" value={stringValue} on:blur={update} class="form-control" />
</ConfigEntry>
