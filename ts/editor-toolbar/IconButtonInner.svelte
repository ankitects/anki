<script lang="typescript">
    export let className: string;
    export let onClick: (event: ClickEvent) => void;
    export let active: boolean;
</script>

<style lang="scss">
    button {
        display: inline-block;
        width: 28px;
        height: 28px;

        vertical-align: -webkit-baseline-middle;
        padding: 2px;

        & > :global(svg),
        & > :global(img) {
            vertical-align: unset;
            width: 100%;
            height: 100%;
        }
    }

    .active {
        border-bottom: 3px solid black;
        border-radius: 3px;

        :global(.nightMode) & {
            border-bottom-color: white;
        }
    }
</style>

<button class={className} class:active on:click={onClick} on:mousedown|preventDefault>
    <slot />
</button>
