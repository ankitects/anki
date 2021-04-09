<script lang="typescript">
    import SquareButton from "./SquareButton.svelte";

    export let id: string;
    export let className = "";
    export let tooltip: string;
    export let disables = true;
    export let dropdownToggle = false;

    export let icon = "";
    export let onClick: (event: MouseEvent) => void;
</script>

<SquareButton {id} {className} {tooltip} {onClick} {disables} {dropdownToggle} on:mount>
    {@html icon}
</SquareButton>
