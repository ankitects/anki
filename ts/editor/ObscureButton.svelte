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
    import { forEditorField } from ".";

    import WithShortcut from "components/WithShortcut.svelte";
    import IconButton from "components/IconButton.svelte";

    let obscureMode = true;
    let icon = eyeIcon;

    function toggleObscure(): void {
        obscureMode = !obscureMode;
    }

    const cardFormat = getContext<Readable<"question" | "answer">>(cardFormatKey);

    $: obscure = obscureMode && $cardFormat === "question";

    $: if (obscure) {
        icon = eyeSlashIcon;
        forEditorField([], (field) => field.editingArea.obscure());
    } else {
        icon = eyeIcon;
        forEditorField([], (field) => field.editingArea.unobscure());
    }
</script>

<WithShortcut shortcut={"Control+P"} let:createShortcut>
    <IconButton
        tooltip="Obscure fields while displaying front side"
        on:click={toggleObscure}
        on:mount={createShortcut}
        active={obscureMode}
    >
        {@html icon}
    </IconButton>
</WithShortcut>
