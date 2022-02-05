<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { MatchResult, SurroundFormat } from "../../domlib/surround";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getSurrounder } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    function isFontElement(element: Element): element is HTMLFontElement {
        return element.tagName === "FONT";
    }

    const surroundElement = document.createElement("span");

    function matcher(element: Element): Exclude<MatchResult, MatchResult.ALONG> {
        if (!(element instanceof HTMLElement) && !(element instanceof SVGElement)) {
            return MatchResult.NO_MATCH;
        }

        if (isFontElement(element) && element.hasAttribute("color")) {
            return MatchResult.MATCH;
        }

        if (element.style.getPropertyValue("color").length > 0) {
            return MatchResult.KEEP;
        }

        return MatchResult.NO_MATCH;
    }

    function clearer(element: Element): boolean {
        const htmlElement = element as HTMLElement | SVGElement;
        htmlElement.style.removeProperty("color");

        if (htmlElement.style.cssText.length === 0) {
            htmlElement.removeAttribute("style");
        }

        return !htmlElement.hasAttribute("style") && element.className.length === 0;
    }

    const generalFormat: SurroundFormat = {
        surroundElement,
        matcher,
        clearer,
    };

    function createFormat(color: string): SurroundFormat {
        const surroundElement = document.createElement("span");
        surroundElement.style.color = color;

        function matcher(element: Element): Exclude<MatchResult, MatchResult.ALONG> {
            if (!(element instanceof HTMLElement) && !(element instanceof SVGElement)) {
                return MatchResult.NO_MATCH;
            }

            if (isFontElement(element) && element.color === color) {
                return MatchResult.MATCH;
            }

            if (element.style.color === color) {
                return MatchResult.KEEP;
            }

            return MatchResult.NO_MATCH;
        }

        return {
            surroundElement,
            matcher,
            clearer,
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
