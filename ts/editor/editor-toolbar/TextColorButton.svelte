<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import type { Match } from "../../domlib/surround";
    import { MatchType, SurroundFormat } from "../../domlib/surround";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getSurrounder, removeEmptyStyle } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    function isFontElement(element: Element): element is HTMLFontElement {
        return element.tagName === "FONT";
    }

    const surroundElement = document.createElement("span");

    function clear(element: HTMLElement | SVGElement): boolean {
        element.style.removeProperty("color");
        return removeEmptyStyle(element) && element.className.length === 0;
    }

    function matcher(element: HTMLElement | SVGElement): Match {
        if (isFontElement(element) && element.hasAttribute("color")) {
            return { type: MatchType.MATCH };
        }

        if (element.style.getPropertyValue("color").length > 0) {
            return {
                type: MatchType.CLEAR,
                clear,
            };
        }

        return { type: MatchType.NONE };
    }

    const generalFormat: SurroundFormat = {
        surroundElement,
        matcher,
    };

    function createFormat(color: string): SurroundFormat {
        const surroundElement = document.createElement("span");
        surroundElement.style.color = color;

        function matcher(element: Element): Match {
            if (!(element instanceof HTMLElement) && !(element instanceof SVGElement)) {
                return { type: MatchType.NONE };
            }

            if (isFontElement(element) && element.color === color) {
                return { type: MatchType.MATCH };
            }

            if (element.style.color === color) {
                return { type: MatchType.CLEAR, clear };
            }

            return { type: MatchType.NONE };
        }

        return {
            surroundElement,
            matcher,
        };
    }

    export let color: string;
    $: format = createFormat(color);

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.push(generalFormat);

    const { focusedInput } = noteEditorContext.get();
    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getSurrounder(input);

    function setTextColor(): void {
        surrounder?.surroundCommand(format, [generalFormat]);
    }

    const forecolorKeyCombination = "F7";
    const forecolorPickKeyCombination = "F8";
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip="{tr.editingSetTextColor()} ({getPlatformString(
            forecolorKeyCombination,
        )})"
        {disabled}
        on:click={setTextColor}
        --border-left-radius="5px"
    >
        {@html textColorIcon}
        {@html colorHelperIcon}
    </IconButton>
    <Shortcut keyCombination={forecolorKeyCombination} on:action={setTextColor} />

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
                format = createFormat(setColor(event));
                setTextColor();
            }}
        />
    </IconButton>
    <Shortcut
        keyCombination={forecolorPickKeyCombination}
        on:action={(event) => {
            const textColor = setColor(event);
            bridgeCommand(`lastTextColor:${textColor}`);
            format = createFormat(setColor(event));
            setTextColor();
        }}
    />
</WithColorHelper>
