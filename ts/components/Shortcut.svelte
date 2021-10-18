<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onMount } from "svelte";
    import { registerShortcut } from "../lib/shortcuts";

    export let keyCombination: string;
    export let target: EventTarget | Document = document;

    const dispatch = createEventDispatcher();

    onMount(() =>
        registerShortcut(
            (event: KeyboardEvent) => dispatch("action", { originalEvent: event }),
            keyCombination,
            target as any
        )
    );
</script>
