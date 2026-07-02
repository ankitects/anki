<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithTooltip from "$lib/components/WithTooltip.svelte";

    import TableCell from "./TableCell.svelte";

    let className = "";
    export { className as class };
    export let tooltip: string;
</script>

<WithTooltip {tooltip} let:createTooltip>
    <TableCell
        class={className}
        on:mount={(event) => createTooltip(event.detail.element)}
    >
        <slot />
    </TableCell>
</WithTooltip>
