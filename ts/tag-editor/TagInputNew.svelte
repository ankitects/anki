<script lang="typescript">
    import TagInput from "./TagInput.svelte";

    export let name;

    function onKeydown(event: KeyboardEvent): void {
        console.log("onkeydown");
    }
</script>

<TagInput bind:name on:keydown={onKeydown} on:blur />
