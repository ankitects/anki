<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { stepsToString, stringToSteps } from "./steps";
    import RevertButton from "./RevertButton.svelte";
    import type { NumberValueEvent } from "./events";

    export let value: number[];
    export let defaultValue: number[];

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

<input type="text" value={stringValue} on:blur={update} class="form-control" />
<RevertButton bind:value {defaultValue} on:revert={revert} />
