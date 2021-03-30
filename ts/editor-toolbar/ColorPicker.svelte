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
            box-shadow: inset 0 0 12px 4px rgb(0 0 0 / 30%);
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
        width: 28px;
        height: calc(28px - 4px);

        cursor: pointer;
    }
</style>

<button tabindex="-1" {id} class={className} {...props} on:mousedown|preventDefault>
    <span> <input type="color" on:change={onChange} /> </span>
</button>
