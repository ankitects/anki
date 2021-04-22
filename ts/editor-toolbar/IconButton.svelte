<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import SquareButton from "./SquareButton.svelte";

    export let id: string;
    export let className = "";
    export let tooltip: string;
    export let shortcutLabel: string | undefined;
    export let icon: string;

    export let onClick: (event: MouseEvent) => void;

    export let disables = true;
    export let dropdownToggle = false;
</script>

<SquareButton
    {id}
    {className}
    {tooltip}
    {shortcutLabel}
    {onClick}
    {disables}
    {dropdownToggle}
    on:mount>
    {@html icon}
</SquareButton>
