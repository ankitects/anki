<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ButtonGroupItem from "./ButtonGroupItem.svelte";
    import SquareButton from "./SquareButton.svelte";

    export let id: string;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined;
    export let disables = true;
    export let dropdownToggle = false;
</script>

<SquareButton {id} {className} {tooltip} {disables} {dropdownToggle} on:click on:mount>
    <slot />
</SquareButton>
