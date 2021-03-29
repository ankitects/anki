<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";
</script>

<style lang="scss">
    ul {
        padding-inline-start: 0;

        margin-left: 0.25rem;
        margin-right: 0.25rem;

        & :global(button) {
            margin-left: -1px;
        }

        & > :global(li:nth-child(1) button) {
            margin-left: 0px;
            border-top-left-radius: 0.35rem;
            border-bottom-left-radius: 0.35rem;
        }

        & > :global(li:nth-last-child(1) button) {
            border-top-right-radius: 0.35rem;
            border-bottom-right-radius: 0.35rem;
        }
    }
</style>

<ButtonItem>
    <ul>
        <slot />
    </ul>
</ButtonItem>
