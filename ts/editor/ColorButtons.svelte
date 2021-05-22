<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import ColorPicker from "components/ColorPicker.svelte";
    import WithShortcut from "components/WithShortcut.svelte";

    import { squareFillIcon } from "./icons";
    import { appendInParentheses } from "./helpers";

    import "./color.css";

    export let api = {};

    const foregroundColorKeyword = "--foreground-color";
    let color = "black";

    $: {
        document.documentElement.style.setProperty(foregroundColorKeyword, color);
    }

    function wrapWithForecolor(): void {
        document.execCommand("forecolor", false, color);
    }

    function setWithCurrentColor({ currentTarget }: Event): void {
        color = (currentTarget as HTMLInputElement).value;
    }
</script>

<ButtonGroup {api}>
    <ButtonGroupItem>
        <WithShortcut shortcut={'F7'} let:createShortcut let:shortcutLabel>
            <IconButton
                class="forecolor"
                tooltip={appendInParentheses(tr.editingSetForegroundColor(), shortcutLabel)}
                on:click={wrapWithForecolor}
                on:mount={createShortcut}>
                {@html squareFillIcon}
            </IconButton>
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithShortcut shortcut={'F8'} let:createShortcut let:shortcutLabel>
            <ColorPicker
                tooltip={appendInParentheses(tr.editingChangeColor(), shortcutLabel)}
                on:change={setWithCurrentColor}
                on:mount={createShortcut} />
        </WithShortcut>
    </ButtonGroupItem>
</ButtonGroup>
