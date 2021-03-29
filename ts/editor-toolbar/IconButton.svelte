<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";

    export let className: string;
    export let icon: string;
    export let onClick: (event: ClickEvent) => void;
</script>

<style lang="scss">
    span {
        display: inline-block;
        width: 28px;
        height: 28px;
        vertical-align: -webkit-baseline-middle;

        & > :global(svg),
        & > :global(img) {
            vertical-align: unset;
            width: 100%;
            height: 100%;
        }
    }
</style>

<ButtonItem>
    <span class={className} on:click={onClick} on:mousedown|preventDefault>
        {#if icon}
            {@html icon}
        {/if}
    </span>
</ButtonItem>
