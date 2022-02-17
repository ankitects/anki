<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import type {
        MatchType,
        SurroundFormat,
        ElementNode,
        FormattingNode,
    } from "../../domlib/surround";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getBaseSurrounder, removeEmptyStyle } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let color: string;

    function isFontElement(element: Element): element is HTMLFontElement {
        return element.tagName === "FONT";
    }

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (isFontElement(element) && element.hasAttribute("color")) {
            match.setCache(element.getAttribute("color"));
            return match.remove();
        }

        const value = element.style.getPropertyValue("color");

        if (value.length > 0) {
            match.setCache(value);

            return match.clear((): void => {
                element.style.removeProperty("color");

                if (removeEmptyStyle(element) && element.className.length === 0) {
                    match.remove();
                }
            });
        }
    }

    function merger(before: FormattingNode, after: FormattingNode): boolean {
        if (before.matchLeaves.length === 0 || after.matchLeaves.length === 0) {
            return true;
        }

        const firstBefore = before.matchLeaves[0];
        const firstAfter = after.matchLeaves[0];

        if (firstBefore.match.cache == firstAfter.match.cache) {
            return true;
        }

        return false;
    }

    function ascender(node: FormattingNode, elementNode: ElementNode): boolean {
        if (node.matchLeaves.length === 0 || !elementNode.match.matches) {
            return true;
        }

        const first = node.matchLeaves[0];
        if (first.match.cache === elementNode.match.cache) {
            return true;
        }

        return false;
    }

    function formatter(node: FormattingNode): boolean {
        const span = document.createElement("span");

        if (node.matchLeaves.length > 0) {
            const first = node.matchLeaves[0];
            span.style.setProperty("color", first.match.cache);
        } else {
            span.style.setProperty("color", color);
        }

        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const format: SurroundFormat = {
        matcher,
        merger,
        ascender,
        formatter,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.push(format);

    const { focusedInput } = noteEditorContext.get();
    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getBaseSurrounder(input, format);

    function setTextColor(): void {
        surrounder?.surroundCommand(format);
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
                color = setColor(event);
                bridgeCommand(`lastTextColor:${color}`);
                setTextColor();
            }}
        />
    </IconButton>
    <Shortcut
        keyCombination={forecolorPickKeyCombination}
        on:action={(event) => {
            color = setColor(event);
            bridgeCommand(`lastTextColor:${color}`);
            setTextColor();
        }}
    />
</WithColorHelper>
