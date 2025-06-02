<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";
    import * as tr from "@generated/ftl";

    export let value: number[];
    export let defaults: number[];

    let stringValue: string;
    let taRef: HTMLTextAreaElement;

    function updateHeight() {
        if (taRef) {
            taRef.style.height = "auto";
            // +2 for "overflow-y: auto" in case js breaks
            taRef.style.height = `${taRef.scrollHeight + 2}px`;
        }
    }

    $: {
        stringValue = render(value);
        tick().then(updateHeight);
    }

    function render(params: number[]): string {
        return params.map((v) => v.toFixed(4)).join(", ");
    }

    const validParamCounts = [0, 17, 19, 21];

    function update(e: Event): void {
        const input = e.target as HTMLInputElement;
        let newValue = input.value
            .replace(/ /g, "")
            .split(",")
            .filter((e) => e)
            .map((v) => Number(v));

        if (validParamCounts.includes(newValue.length)) {
            value = newValue;
        } else {
            alert(tr.deckConfigInvalidParameters());
            input.value = stringValue;
        }
    }
</script>

<svelte:window onresize={updateHeight} />

<textarea
    bind:this={taRef}
    value={stringValue}
    on:blur={update}
    class="w-100"
    placeholder={render(defaults)}
></textarea>

<style>
    textarea {
        resize: none;
        overflow-y: auto;
    }
</style>
