<script lang="typescript">
    import TagInput from "./TagInput.svelte";

    export let name: string;
    export let input: HTMLInputElement;

    function onKeydown(event: KeyboardEvent): void {
        console.log("onkeydown");
    }
</script>

<TagInput bind:name bind:input on:keydown={onKeydown} on:blur />
