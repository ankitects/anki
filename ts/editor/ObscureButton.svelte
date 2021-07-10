<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import { writable } from "svelte/store";

    export const obscure = writable<boolean>(false);
</script>

<script lang="typescript">
    /* import * as tr from "lib/i18n"; */
    import type { Readable } from "svelte/store";
    import { getContext } from "svelte";
    import { eyeIcon, eyeSlashIcon } from "./icons";
    import { cardFormatKey } from "./context-keys";
    import { getCurrentField } from ".";

    import WithShortcut from "components/WithShortcut.svelte";
    import IconButton from "components/IconButton.svelte";

    let windowFocus = document.hasFocus();

    function windowFocused(): void {
        windowFocus = true;
    }

    function blurIfObscured(): void {
        windowFocus = false;

        if ($obscure) {
            // blur completely
            getCurrentField()?.blur();
        }
    }

    let obscureMode = true;
    let icon = eyeIcon;

    function toggleObscure(): void {
        obscureMode = !obscureMode;
    }

    const cardFormat = getContext<Readable<"question" | "answer">>(cardFormatKey);

    $: $obscure =
        obscureMode &&
        ($cardFormat === "question" || ($cardFormat === "answer" && !windowFocus));

    $: if ($obscure) {
        icon = eyeSlashIcon;
    } else {
        icon = eyeIcon;
    }
</script>

<svelte:window on:focus={windowFocused} on:blur={blurIfObscured} />

<WithShortcut shortcut={"Control+P"} let:createShortcut let:shortcutLabel>
    <IconButton
        tooltip={`Toggle obscure mode (${shortcutLabel})`}
        on:click={toggleObscure}
        on:mount={createShortcut}
        active={obscureMode}
    >
        {@html icon}
    </IconButton>
</WithShortcut>
