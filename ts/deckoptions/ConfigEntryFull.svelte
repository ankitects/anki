<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "./Row.svelte";

    export let id: string | undefined = undefined;
</script>

<Row {id}>
    <div class="col-12 d-flex align-items-center">
        <slot />
    </div>
</Row>
