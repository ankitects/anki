<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Badge from "../components/Badge.svelte";
    import { deleteIcon } from "./icons";

    let className: string = "";
    export { className as class };
</script>

<Badge class="rounded-circle d-flex align-items-center ms-1 {className}" on:click
    >{@html deleteIcon}</Badge
>
