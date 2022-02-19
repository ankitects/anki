<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import type {
        ElementNode,
        FormattingNode,
        MatchType,
        SurroundFormat,
    } from "../../domlib/surround";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { removeEmptyStyle, Surrounder } from "../surround";
    import type { RemoveFormat } from "./EditorToolbar.svelte";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";
    import { pageTheme } from "../../sveltelib/theme";

    export let color: string;

    $: transformedColor = transformColor(color);

    /**
     * The DOM will transform colors such as "#ff0000" to "rgb(255, 0, 0)".
     */
    function transformColor(color: string): string {
        const span = document.createElement("span");
        span.style.setProperty("color", color);
        return span.style.getPropertyValue("color");
    }

    function isFontElement(element: Element): element is HTMLFontElement {
        return element.tagName === "FONT";
    }

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (isFontElement(element) && element.hasAttribute("color")) {
            match.setCache(element.getAttribute("color"));
            return match.remove();
        }

        const value = element.style.getPropertyValue("color");

        if (value.length === 0) {
            return;
        }

        match.setCache(value);
        match.clear((): void => {
            element.style.removeProperty("color");

            if (removeEmptyStyle(element) && element.className.length === 0) {
                match.remove();
            }
        });
    }

    function merger(before: FormattingNode, after: FormattingNode): boolean {
        return before.getCache(transformedColor) === after.getCache(transformedColor);
    }

    function ascender(node: FormattingNode, elementNode: ElementNode): boolean {
        return (
            !elementNode.match.matches ||
            node.getCache(transformedColor) === elementNode.match.cache
        );
    }

    function formatter(node: FormattingNode): boolean {
        const span = document.createElement("span");
        span.style.setProperty("color", node.getCache(transformedColor));
        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const format: SurroundFormat = {
        matcher,
        merger,
        ascender,
        formatter,
    };

    const namedFormat: RemoveFormat = {
        name: tr.editingTextColor(),
        show: true,
        active: true,
        format,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    const { focusedInput } = noteEditorContext.get();
    const surrounder = Surrounder.make();
    let disabled: boolean;

    $: if (editingInputIsRichText($focusedInput)) {
        surrounder.richText = $focusedInput;
        disabled = false;
    } else {
        surrounder.disable();
        disabled = true;
    }

    function setTextColor(): void {
        surrounder.overwriteSurround(format);
    }

    const forecolorKeyCombination = "F7";
    const forecolorPickKeyCombination = "F8";

    $: baseColor = $pageTheme.isDark ? "rgb(255, 255, 255)" : "rgb(0, 0, 0)";

    // NOTE This is a an experiment to counteract the the automatic surrounding of execCommand.
    // I am not sure how well it would harmonize with custom user CSS.
    document.addEventListener(
        "input" as "beforeinput",
        async (event: InputEvent): Promise<void> => {
            if (
                event.inputType.startsWith("delete") &&
                !(await surrounder.isSurrounded(format)) &&
                document.queryCommandValue("foreColor") !== baseColor
            ) {
                document.execCommand("foreColor", false, "false");
            }
        },
    );
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip="{tr.editingTextColor()} ({getPlatformString(forecolorKeyCombination)})"
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
