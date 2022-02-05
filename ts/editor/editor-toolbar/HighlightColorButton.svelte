<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { execCommand } from "../helpers";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { arrowIcon, highlightColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let color: string;

    $: backcolorWrap = wrapWithBackcolor(color);

    const wrapWithBackcolor = (color: string) => () => {
        execCommand("backcolor", false, color);
    };

    const { focusedInput } = context.get();
    $: disabled = !editingInputIsRichText($focusedInput);
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip={tr.editingSetTextHighlightColor()}
        {disabled}
        on:click={backcolorWrap}
    >
        {@html highlightColorIcon}
        {@html colorHelperIcon}
    </IconButton>

    <IconButton
        tooltip={tr.editingChangeColor()}
        widthMultiplier={0.5}
        {disabled}
        --border-right-radius="5px"
    >
        {@html arrowIcon}
        <ColorPicker
            on:change={(event) => {
                const highlightColor = setColor(event);
                bridgeCommand(`lastHighlightColor:${highlightColor}`);
                backcolorWrap = wrapWithBackcolor(highlightColor);
                backcolorWrap();
            }}
        />
    </IconButton>
</WithColorHelper>
