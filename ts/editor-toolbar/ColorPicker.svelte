<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";

    export let className: string;
    export let onChange: (event: ChangeEvent) => void;
</script>

<style lang="scss">
    button {
        display: inline-block;
        width: 28px;
        height: 28px;

        vertical-align: -webkit-baseline-middle;
        padding: 0;
    }

    span {
        display: inline-block;
        width: 100%;
        height: 100%;
    }

    input {
        opacity: 0;
        width: 100%;
        height: 100%;
    }
</style>

<ButtonItem>
    <button>
        <span class={className}> <input type="color" on:change={onChange} /> </span>
    </button>
</ButtonItem>
