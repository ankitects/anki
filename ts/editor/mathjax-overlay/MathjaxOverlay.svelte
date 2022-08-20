<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type CodeMirrorLib from "codemirror";
    import { onMount, tick } from "svelte";
    import { writable } from "svelte/store";

    import Popover from "../../components/Popover.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import WithOverlay from "../../components/WithOverlay.svelte";
    import { placeCaretAfter } from "../../domlib/place-caret";
    import { escapeSomeEntities, unescapeSomeEntities } from "../../editable/mathjax";
    import { Mathjax } from "../../editable/mathjax-element";
    import { hasBlockAttribute } from "../../lib/dom";
    import { on } from "../../lib/events";
    import { noop } from "../../lib/functional";
    import type { Callback } from "../../lib/typing";
    import { singleCallback } from "../../lib/typing";
    import HandleBackground from "../HandleBackground.svelte";
    import { context } from "../rich-text-input";
    import MathjaxButtons from "./MathjaxButtons.svelte";
    import MathjaxEditor from "./MathjaxEditor.svelte";

    const { editable, element, preventResubscription } = context.get();

    let activeImage: HTMLImageElement | null = null;
    let mathjaxElement: HTMLElement | null = null;
    let allow = noop;
    let unsubscribe = noop;

    let selectAll = false;
    let position: CodeMirrorLib.Position | undefined = undefined;

    /**
     * Will contain the Mathjax text with unescaped entities.
     * This is the text displayed in the actual editor window.
     */
    const code = writable("");

    function showHandle(image: HTMLImageElement, pos?: CodeMirrorLib.Position): void {
        allow = preventResubscription();
        position = pos;

        /* Setting the activeImage and mathjaxElement to a non-nullish value is
         * what triggers the Mathjax editor to show */
        activeImage = image;
        mathjaxElement = activeImage.closest(Mathjax.tagName)!;

        code.set(unescapeSomeEntities(mathjaxElement.dataset.mathjax ?? ""));
        unsubscribe = code.subscribe((value: string) => {
            mathjaxElement!.dataset.mathjax = escapeSomeEntities(value);
        });
    }

    function placeHandle(after: boolean): void {
        editable.focusHandler.flushCaret();

        if (after) {
            (mathjaxElement as any).placeCaretAfter();
        } else {
            (mathjaxElement as any).placeCaretBefore();
        }
    }

    async function resetHandle(): Promise<void> {
        selectAll = false;
        position = undefined;

        if (activeImage && mathjaxElement) {
            unsubscribe();
            activeImage = null;
            mathjaxElement = null;
        }

        await tick();
        allow();
    }

    let errorMessage: string;
    let cleanup: Callback | null = null;

    async function updateErrorMessage(): Promise<void> {
        errorMessage = activeImage!.title;
    }

    async function updateImageErrorCallback(image: HTMLImageElement | null) {
        cleanup?.();
        cleanup = null;

        if (!image) {
            return;
        }

        cleanup = on(image, "resize", updateErrorMessage);
    }

    $: updateImageErrorCallback(activeImage);

    async function updateHandle({ target }: Event): Promise<void> {
        await resetHandle();

        if (target instanceof HTMLImageElement && target.dataset.anki === "mathjax") {
            showHandle(target);
        }
    }

    async function showAutofocusHandle({
        detail,
    }: CustomEvent<{
        image: HTMLImageElement;
        position?: [number, number];
    }>): Promise<void> {
        let position: CodeMirrorLib.Position | undefined = undefined;

        await resetHandle();

        if (detail.position) {
            const [line, ch] = detail.position;
            position = { line, ch };
        }

        showHandle(detail.image, position);
    }

    async function showSelectAll({
        detail,
    }: CustomEvent<HTMLImageElement>): Promise<void> {
        await resetHandle();
        selectAll = true;
        showHandle(detail);
    }

    onMount(async () => {
        const container = await element;

        return singleCallback(
            on(container, "click", updateHandle),
            on(container, "movecaretafter" as any, showAutofocusHandle),
            on(container, "selectall" as any, showSelectAll),
        );
    });

    let isBlock: boolean;
    $: isBlock = activeImage ? hasBlockAttribute(activeImage) : false;

    async function updateBlockAttribute(): Promise<void> {
        activeImage.setAttribute("block", String(isBlock));

        // We assume that by the end of this tick, the image will have
        // adjusted its styling to either block or inline
        await tick();
    }

    const acceptShortcut = "Enter";
    const newlineShortcut = "Shift+Enter";
</script>


{#if activeImage && mathjaxElement}
    <WithOverlay
        reference={activeImage}
        padding={8}
        keepOnKeyup
        let:position={positionOverlay}
    >
        <WithFloating
            reference={activeImage}
            placement="auto"
            offset={20}
            keepOnKeyup
            hideIfEscaped
            let:position={positionFloating}
        >
            <Popover slot="floating">
                <MathjaxEditor
                    {acceptShortcut}
                    {newlineShortcut}
                    {code}
                    {selectAll}
                    {position}
                    on:blur={resetHandle}
                    on:moveoutstart={() => {
                        placeHandle(false);
                        resetHandle();
                    }}
                    on:moveoutend={() => {
                        placeHandle(true);
                        resetHandle();
                    }}
                    let:editor={mathjaxEditor}
                >
                    <Shortcut
                        keyCombination={acceptShortcut}
                        on:action={() => {
                            placeHandle(true);
                            resetHandle();
                        }}
                    />

                    <MathjaxButtons
                        {isBlock}
                        on:setinline={async () => {
                            isBlock = false;
                            await updateBlockAttribute();
                            positionOverlay();
                            positionFloating();
                        }}
                        on:setblock={async () => {
                            isBlock = true;
                            await updateBlockAttribute();
                            positionOverlay();
                            positionFloating();
                        }}
                        on:delete={() => {
                            placeCaretAfter(activeImage);
                            activeImage.remove();
                            resetHandle();
                        }}
                        on:surround={async ({ detail }) => {
                            const editor = await mathjaxEditor.editor;
                            const { prefix, suffix } = detail;

                            editor.replaceSelection(prefix + editor.getSelection() + suffix);
                        }}
                    />
                </MathjaxEditor>
            </Popover>
        </WithFloating>

        <svelte:fragment slot="overlay">
            <HandleBackground tooltip={errorMessage} />
        </svelte:fragment>
    </WithOverlay>
{/if}
