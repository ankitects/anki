<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";

    export let className: string;
</script>

<style>
    span {
        font-size: x-large;
    }
</style>

<ButtonItem>
    <span class={className}></span>
</ButtonItem>
