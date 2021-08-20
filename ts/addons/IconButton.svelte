<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import IconButton from "components/IconButton.svelte";

    export let id: string;
    export let className: string;

    export let tooltip: string;
    export let active: boolean;
    export let disabled: boolean;
    export let tabbable: boolean;

    export let iconSize: number;
    export let widthMultiplier: number;

    export let onClick: (event: MouseEvent) => void;
    export let onMount: (event: CustomEvent) => void;
</script>

<IconButton
    {id}
    class={className}
    {tooltip}
    {active}
    {disabled}
    {tabbable}
    {iconSize}
    {widthMultiplier}
    on:click={onClick}
    on:mount={onMount}
>
    <slot />
</IconButton>
