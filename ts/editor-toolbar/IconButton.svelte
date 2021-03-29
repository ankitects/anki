<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";

    export let className: string;
    export let icon: string;
</script>

<style lang="scss">
    span {
        display: inline-block;
        width: 28px;
        height: 28px;
        vertical-align: -webkit-baseline-middle;

        & > :global(svg) {
            width: 100%;
            height: 100%;
        }
    }
</style>

<ButtonItem>
    <span class={className}>{@html icon}</span>
</ButtonItem>
