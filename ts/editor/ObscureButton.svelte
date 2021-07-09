<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    /* import * as tr from "lib/i18n"; */
    import type { Readable } from "svelte/store";
    import { getContext } from "svelte";
    import { eyeIcon, eyeSlashIcon } from "./icons";
    import { cardFormatKey } from "./context-keys";

    import WithShortcut from "components/WithShortcut.svelte";
    import IconButton from "components/IconButton.svelte";

    let obscure = true;
    function toggleObscure(): void {
        obscure = !obscure;
    }

    const cardFormat = getContext<Readable<"question" | "answer">>(cardFormatKey);

    $: icon = obscure && $cardFormat === "question" ? eyeSlashIcon : eyeIcon;
</script>

<WithShortcut shortcut={"Control+P"} let:createShortcut>
    <IconButton
        tooltip="Obscure fields while displaying front side"
        on:click={toggleObscure}
        on:mount={createShortcut}
        active={obscure}
    >
        {@html icon}
    </IconButton>
</WithShortcut>
