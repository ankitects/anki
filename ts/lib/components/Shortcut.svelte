<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { preventDefault } from "@tslib/events";
    import { registerShortcut } from "@tslib/shortcuts";
    import { createEventDispatcher, onMount } from "svelte";

    export let keyCombination: string;
    export let event: "keydown" | "keyup" | undefined = undefined;

    const dispatch = createEventDispatcher();

    onMount(() =>
        registerShortcut(
            (event: KeyboardEvent) => {
                preventDefault(event);
                dispatch("action", { originalEvent: event });
            },
            keyCombination,
            { event },
        ),
    );
</script>
