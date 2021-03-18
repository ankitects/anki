<script lang="typescript">
    export let name: string = "";

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        }

        else if (event.code === "Enter") {
        }
    }

    function onBlur(event) {
    }
</script>

<style lang="scss">
    input {
        color: var(--text-fg);
        background-color: transparent;

        border: none;

        &:focus {
            outline: none;
        }
    }
</style>

<input type="text" bind:value={name} on:keydown={onKeydown} on:blur={onBlur} />
