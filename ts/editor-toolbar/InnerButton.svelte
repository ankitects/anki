<script lang="typescript">
    export let className: string = "";
    export let onClick: (event: ClickEvent) => void;
    export let active = false;
    export let disabled = false;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: 30px;
        height: 30px;

        background-color: white;

        & > :global(svg),
        & > :global(img) {
            vertical-align: unset;
            width: 100%;
            height: 100%;
        }

        &:hover {
            background-color: #eee;
        }

        &:active,
        &.active {
            box-shadow: inset 0 0 10px 3px rgb(0 0 0 / 30%);
        }

        &[disabled] {
            opacity: 0.4;

            &:hover {
                background-color: white;
            }

            &:active,
            &.active {
                box-shadow: none;
            }
        }
    }
</style>

<button
    class="p-1 {className}"
    class:active
    {disabled}
    on:click={onClick}
    on:mousedown|preventDefault>
    <slot />
</button>
