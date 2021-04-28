<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import IconButton from "components/IconButton.svelte";
    import ColorPicker from "components/ColorPicker.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import WithShortcut from "components/WithShortcut.svelte";

    import { squareFillIcon } from "./icons";

    import "./color.css";

    const foregroundColorKeyword = "--foreground-color";

    function setForegroundColor(color: string): void {
        document.documentElement.style.setProperty(foregroundColorKeyword, color);
    }

    function getForecolor(): string {
        return document.documentElement.style.getPropertyValue(foregroundColorKeyword);
    }

    function wrapWithForecolor(color: string): void {
        document.execCommand("forecolor", false, color);
    }

    function setWithCurrentColor({ currentTarget }: Event): void {
        return setForegroundColor((currentTarget as HTMLInputElement).value);
    }
</script>

<ButtonGroup id="color">
    <WithShortcut shortcut="F7" let:createShortcut let:shortcutLabel>
        <IconButton
            class="forecolor"
            tooltip={`${tr.editingSetForegroundColor} (${shortcutLabel})`}
            on:click={() => wrapWithForecolor(getForecolor())}
            on:mount={createShortcut}>
            {@html squareFillIcon}
        </IconButton>
    </WithShortcut>

    <WithShortcut shortcut="F8" let:createShortcut let:shortcutLabel>
        <ColorPicker
            tooltip={`${tr.editingChangeColor()} (${shortcutLabel})`}
            on:change={setWithCurrentColor}
            on:mount={createShortcut} />
    </WithShortcut>
</ButtonGroup>
