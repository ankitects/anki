<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { withoutUnicodeIsolation } from "@tslib/i18n";
    import { slide } from "svelte/transition";

    import Row from "$lib/components/Row.svelte";

    export let warning: string;
    export let className = "alert-warning";
</script>

{#if warning}
    <Row class="centre m-3">
        <div class="col-12 alert {className} mb-0" in:slide out:slide>
            <slot name="icon" />
            {withoutUnicodeIsolation(warning)}
        </div>
    </Row>
{/if}

<style lang="scss">
    .centre {
        justify-content: center;
    }
</style>
