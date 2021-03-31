<script lang="typescript">
    import SquareButton from "./SquareButton.svelte";

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    export let icon = "";
    export let onClick: (event: ClickEvent) => void;
</script>

<SquareButton {id} {className} {props} {onClick} on:mount>
    {@html icon}
</SquareButton>
