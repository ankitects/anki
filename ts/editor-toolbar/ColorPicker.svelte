<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";

    export let className: string;
    export let onChange: (event: ChangeEvent) => void;
</script>

<style lang="scss">
    span {
        display: inline-block;
        width: 28px;
        height: 28px;
        vertical-align: -webkit-baseline-middle;
    }

    input {
        opacity: 0;
        width: 100%;
        height: 100%;
    }
</style>

<ButtonItem>
    <span class={className}> <input type="color" on:change={onChange} /> </span>
</ButtonItem>
