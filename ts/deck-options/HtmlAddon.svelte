<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    export let html: string;
    export let mounted: () => void;

    onMount(mounted);
</script>

{@html html}
