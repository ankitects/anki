<script lang="typescript">
    import SquareButton from "./SquareButton.svelte";

    export let id = "";
    export let className = "";
    export let tooltip: string;

    export let icon = "";
    export let onClick: (event: MouseEvent) => void;
</script>

<SquareButton {id} {className} {tooltip} {onClick} {...$$restProps} on:mount>
    {@html icon}
</SquareButton>
