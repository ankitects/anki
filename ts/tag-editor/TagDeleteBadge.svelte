<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import { deleteIcon } from "./icons";

    let className: string = "";
    export { className as class };
</script>

<Badge class="d-flex align-items-center ms-1 {className}" on:click iconSize={80}
    >{@html deleteIcon}</Badge
>
