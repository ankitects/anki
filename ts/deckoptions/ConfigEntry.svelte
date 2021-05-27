<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "./Row.svelte";

    export let id: string | undefined = undefined;
    export let wrap = false;
</script>

<Row {id}>
    <div class="col-7 d-flex align-items-center">
        <slot name="left" />
    </div>

    <div class:col-5={!wrap} class:col-sm-5={wrap} class="d-flex align-items-center">
        <slot name="right" />
    </div>
</Row>
