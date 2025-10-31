<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    export let showFloating = false;
</script>

<div>
    <WithFloating show={showFloating} inline on:close={() => (showFloating = false)}>
        <slot slot="reference" name="button"></slot>

        <Popover slot="floating">
            <slot name="items" />
        </Popover>
    </WithFloating>
</div>
