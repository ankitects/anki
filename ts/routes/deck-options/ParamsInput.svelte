<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    // Host timing preferences must survive the FSRS controls being unmounted.
    let unlock_click_timeout_ms = 500;

    function setParameterUnlockClickTimeoutMs(ms: number): void {
        unlock_click_timeout_ms = ms;
    }

    globalThis.anki ||= {};
    globalThis.anki.setParameterUnlockClickTimeoutMs = setParameterUnlockClickTimeoutMs;
    globalThis.anki.defaultParameterUnlockClickTimeoutMs = unlock_click_timeout_ms;
</script>

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
            // Include the textarea's 1px top and bottom borders.
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

    let clickTimeout: ReturnType<typeof setTimeout>;

    function onClick() {
        clickCount += 1;
        clearTimeout(clickTimeout);
        if (clickCount < UNLOCK_EDIT_COUNT) {
            clickTimeout = setTimeout(() => {
                clickCount = 0;
            }, unlock_click_timeout_ms);
        } else {
            taRef.focus();
        }
    }

    $: unlocked = clickCount >= UNLOCK_EDIT_COUNT;
    $: unlockEditWarning = unlocked ? tr.deckConfigManualParameterEditWarning() : "";
</script>

<svelte:window onresize={updateHeight} />

<div
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
</div>

<Warning warning={unlockEditWarning} className="alert-danger"></Warning>

<style>
    textarea {
        resize: none;
        overflow-y: auto;
    }

    textarea:disabled {
        overflow-y: hidden;
        pointer-events: none;
    }
</style>
