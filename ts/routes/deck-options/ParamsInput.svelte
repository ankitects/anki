<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";
    import * as tr from "@generated/ftl";

    export let value: number[];

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
        const input = e.target as HTMLTextAreaElement;
        const newValue = input.value
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

    const UNLOCK_EDIT_COUNT = 3;
    let clickCount = 0;
    function onClick() {
        clickCount += 1;
        if (clickCount == UNLOCK_EDIT_COUNT) {
            alert(tr.deckConfigManualParameterEditWarning());
        }
    }
</script>

<svelte:window onresize={updateHeight} />

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div on:click={onClick}>
    <textarea
        bind:this={taRef}
        value={stringValue}
        on:blur={update}
        class="w-100"
        placeholder={tr.deckConfigPlaceholderParameters()}
        disabled={clickCount < UNLOCK_EDIT_COUNT}
    ></textarea>
</div>

<style>
    textarea {
        resize: none;
        overflow-y: auto;
    }

    textarea:disabled {
        pointer-events: none;
    }
</style>
