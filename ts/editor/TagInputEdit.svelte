<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount } from "svelte";
    import TagInput from "./TagInput.svelte";

    export let name: string;

    let input: HTMLInputElement;

    function moveCursorToEnd(element: HTMLInputElement): void {
        element.selectionStart = element.selectionEnd = element.value.length;
    }

    onMount(() => {
        // Make sure Autocomplete was fully mounted
        setTimeout(() => {
            moveCursorToEnd(input);
            input.focus();
        }, 0);
    });

    function onKeydown(): void {
        console.log("onkeydown");
    }

    function stopPropagation(event: Event): void {
        event.stopPropagation();
    }
</script>

<TagInput
    bind:name
    bind:input
    on:keydown={onKeydown}
    on:click={stopPropagation}
    on:focusout
    on:update
    on:add
/>
