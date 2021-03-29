<script lang="typescript">
    export let className: string;
    export let onClick: (event: ClickEvent) => void;
    export let active: boolean;
</script>

<style lang="scss">
    button {
        display: inline-block;
        width: 30px;
        height: 30px;

        vertical-align: -webkit-baseline-middle;
        padding: 2px;

        & > :global(svg),
        & > :global(img) {
            vertical-align: unset;
            width: 100%;
            height: 100%;
        }

        &:hover {
            background-color: #eee;
        }

        &:active, &.active {
            box-shadow: inset 0 0 10px 3px rgb(0 0 0 / 30%);
        }
    }
</style>

<button class={className} class:active on:click={onClick} on:mousedown|preventDefault>
    <slot />
</button>
