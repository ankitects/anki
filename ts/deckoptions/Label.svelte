<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, createEventDispatcher } from "svelte";

    let forId: string;
    export { forId as for };

    const dispatch = createEventDispatcher();

    let spanRef: HTMLSpanElement;

    onMount(() => {
        dispatch("mount", { span: spanRef });
    });
</script>

<label bind:this={spanRef} for={forId}><slot /></label>
