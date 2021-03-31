<script lang="typescript">
    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    export let onChange: (event: ChangeEvent) => void;
</script>

<style lang="scss">
    button {
        display: inline-block;
        padding: 0;

        &:hover {
            background-color: #eee;
        }

        &:active {
            box-shadow: inset 0 0 calc(var(--toolbar-size) / 2.5)
                calc(var(--toolbar-size) / 7.5) rgb(0 0 0 / 30%);
            border-color: #aaa;
        }
    }

    span {
        display: inline-block;
        vertical-align: middle;
        width: 100%;
        height: 100%;

        cursor: pointer;
    }

    input {
        display: inline-block;
        opacity: 0;
        width: calc(var(--toolbar-size) - 6px);
        height: calc(var(--toolbar-size) - 6px);

        cursor: pointer;
    }
</style>

<button tabindex="-1" {id} class={className} {...props} on:mousedown|preventDefault>
    <span> <input type="color" on:change={onChange} /> </span>
</button>
