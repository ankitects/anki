<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import TagInput from "./TagInput.svelte";

    export let input: HTMLInputElement;

    let name = "";
    let active = true;

    const dispatch = createEventDispatcher();

    function onKeydown(): void {
        console.log("onkeydown");
    }

    function translateToAdd({ detail }: CustomEvent): void {
        if (name) {
            dispatch("add", detail);
            name = "";
        }
    }
</script>

<TagInput
    bind:name
    bind:input
    on:keydown={onKeydown}
    on:add
    on:update={translateToAdd}
    on:blur
/>
