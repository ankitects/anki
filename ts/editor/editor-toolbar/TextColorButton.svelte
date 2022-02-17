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
    import { getSurrounder, removeEmptyStyle } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let color: string;

    function isFontElement(element: Element): element is HTMLFontElement {
        return element.tagName === "FONT";
    }

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (isFontElement(element) && element.hasAttribute("color")) {
            return match.remove();
        }

        if (element.style.getPropertyValue("color").length > 0) {
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

        const firstBefore = before.matchLeaves[0].element as HTMLElement | SVGElement;
        const firstAfter = after.matchLeaves[0].element as HTMLElement | SVGElement;

        if (
            firstBefore.style.getPropertyValue("color") ===
            firstAfter.style.getPropertyValue("color")
        ) {
            return true;
        }

        return false;
    }

    function ascender(node: FormattingNode, elementNode: ElementNode): boolean {
        if (node.matchLeaves.length === 0 || !elementNode.match.marked) {
            return true;
        }

        const first = node.matchLeaves[0].element as HTMLElement | SVGElement;
        const matchElement = elementNode.element as HTMLElement | SVGElement;

        if (
            first.style.getPropertyValue("color") ===
            matchElement.style.getPropertyValue("color")
        ) {
            return true;
        }

        return false;
    }

    function formatter(node: FormattingNode): boolean {
        const span = document.createElement("span");

        if (node.insideRange && node.matchLeaves.length > 0) {
            span.style.color = (node.matchLeaves[0].element as HTMLElement).style.color;
        } else {
            span.style.color = color;
        }
        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const generalFormat: SurroundFormat = {
        formatter,
        matcher,
        merger,
        ascender,
    };

    function createFormat(color: string): SurroundFormat {
        const surroundElement = document.createElement("span");
        surroundElement.style.color = color;

        function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
            if (isFontElement(element) && element.color === color) {
                return match.remove();
            }

            if (element.style.color === color) {
                return match.clear((): void => {
                    element.style.removeProperty("color");

                    if (removeEmptyStyle(element) && element.className.length === 0) {
                        match.remove();
                    }
                });
            }
        }

        return {
            surroundElement,
            matcher,
        };
    }

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
