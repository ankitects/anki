<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import CardEditingEditor from "./CardEditingEditor.svelte";

    export let uiResolve: () => void;

    onMount(uiResolve);
</script>

<CardEditingEditor />
