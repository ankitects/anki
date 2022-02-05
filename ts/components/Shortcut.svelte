<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import { preventDefault } from "../lib/events";
    import { registerShortcut } from "../lib/shortcuts";

    export let keyCombination: string;

    const dispatch = createEventDispatcher();

    onMount(() =>
        registerShortcut((event: KeyboardEvent) => {
            preventDefault(event);
            dispatch("action", { originalEvent: event });
        }, keyCombination),
    );
</script>
