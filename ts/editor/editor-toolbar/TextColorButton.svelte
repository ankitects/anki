<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { execCommand } from "../helpers";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let color: string;

    $: forecolorWrap = wrapWithForecolor(color);

    const wrapWithForecolor = (color: string) => () => {
        execCommand("forecolor", false, color);
    };

    const { focusedInput } = context.get();
    $: disabled = !editingInputIsRichText($focusedInput);

    const forecolorKeyCombination = "F7";
    const forecolorPickKeyCombination = "F8";
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip="{tr.editingSetTextColor()} ({getPlatformString(
            forecolorKeyCombination,
        )})"
        {disabled}
        on:click={forecolorWrap}
        --border-left-radius="5px"
    >
        {@html textColorIcon}
        {@html colorHelperIcon}
    </IconButton>
    <Shortcut keyCombination={forecolorKeyCombination} on:action={forecolorWrap} />

    <IconButton
        tooltip="{tr.editingChangeColor()} ({getPlatformString(
            forecolorKeyCombination,
        )})"
        {disabled}
        widthMultiplier={0.5}
    >
        {@html arrowIcon}
        <ColorPicker
            on:change={(event) => {
                const textColor = setColor(event);
                bridgeCommand(`lastTextColor:${textColor}`);
                forecolorWrap = wrapWithForecolor(setColor(event));
                forecolorWrap();
            }}
        />
    </IconButton>
    <Shortcut
        keyCombination={forecolorPickKeyCombination}
        on:action={(event) => {
            const textColor = setColor(event);
            bridgeCommand(`lastTextColor:${textColor}`);
            forecolorWrap = wrapWithForecolor(setColor(event));
            forecolorWrap();
        }}
    />
</WithColorHelper>
