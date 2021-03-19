<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    import TagInput from "./TagInput.svelte";

    export let input: HTMLInputElement;
    let name = "";

    const dispatch = createEventDispatcher();

    function onKeydown(event: KeyboardEvent): void {
        console.log("onkeydown");
    }

    function translateToAdd({ detail }: Event): void {
        if (name) {
            dispatch("add", detail)
            name = "";
        }
    }
</script>

<TagInput bind:name bind:input on:keydown={onKeydown} on:add on:update={translateToAdd} />
