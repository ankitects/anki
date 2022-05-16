<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import NotetypeButtons from "./NotetypeButtons.svelte";

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<ButtonToolbar {size} {wrap}>
    <NotetypeButtons />
</ButtonToolbar>
