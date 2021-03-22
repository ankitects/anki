<script lang="typescript">
    import { onMount } from "svelte";

    import TagInput from "./TagInput.svelte";

    export let name;

    let input: HTMLInputElement;

    function moveCursorToEnd(element: Element): void {
        element.selectionStart = element.selectionEnd = element.value.length;
    }

    onMount(() => {
        // Make sure Autocomplete was fully mounted
        setTimeout(() => {
            moveCursorToEnd(input);
            input.focus();
        }, 0)
    });

    function onKeydown(event: KeyboardEvent): void {
        console.log("onkeydown");
    }
</script>

<TagInput bind:name bind:input on:keydown={onKeydown} on:update on:add />
