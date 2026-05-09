<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "$lib/components/Badge.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import { deleteIcon } from "$lib/components/icons";

    let className: string = "";
    export { className as class };
</script>

<Badge class="d-flex align-items-center ms-1 {className}" on:click iconSize={80}>
    <Icon icon={deleteIcon} />
</Badge>
