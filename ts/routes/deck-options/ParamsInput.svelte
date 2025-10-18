<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";
    import * as tr from "@generated/ftl";
    import Warning from "./Warning.svelte";

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
    const UNLOCK_CLICK_TIMEOUT_MS = 500;
    let clickCount = 0;

    let clickTimeout: ReturnType<typeof setTimeout>;

    function onClick() {
        clickCount += 1;
        clearTimeout(clickTimeout);
        if (clickCount < UNLOCK_EDIT_COUNT) {
            clickTimeout = setTimeout(() => {
                clickCount = 0;
            }, UNLOCK_CLICK_TIMEOUT_MS);
        } else {
            taRef.focus();
        }
    }

    $: unlocked = clickCount >= UNLOCK_EDIT_COUNT;
    $: unlockEditWarning = unlocked ? tr.deckConfigManualParameterEditWarning() : "";
</script>

<svelte:window onresize={updateHeight} />

<span
    on:click={onClick}
    on:keypress={onClick}
    role="button"
    aria-label={"FSRS Parameters"}
    tabindex={unlocked ? -1 : 0}
>
    <textarea
        bind:this={taRef}
        value={stringValue}
        on:blur={update}
        class="w-100"
        placeholder={tr.deckConfigPlaceholderParameters()}
        disabled={!unlocked}
    ></textarea>
</span>

<Warning warning={unlockEditWarning} className="alert-danger"></Warning>

<style>
    textarea {
        resize: none;
        overflow-y: auto;
    }

    textarea:disabled {
        pointer-events: none;
    }
</style>
