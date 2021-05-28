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
    {#if $$slots.center}
        <div class="col-7 d-flex align-items-center">
            <slot name="left" />
        </div>

        <div class="col-4 d-flex align-items-center">
            <slot name="center" />
        </div>
    {:else}
        <div class="col-11 d-flex align-items-center">
            <slot name="left" />
        </div>
    {/if}

    <div class="col-1 d-flex align-items-center">
        <slot name="right" />
    </div>
</Row>
