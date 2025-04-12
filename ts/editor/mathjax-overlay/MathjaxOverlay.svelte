<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { hasBlockAttribute } from "@tslib/dom";
    import { on } from "@tslib/events";
    import { promiseWithResolver } from "@tslib/promise";
    import type { Callback } from "@tslib/typing";
    import { singleCallback } from "@tslib/typing";
    import type CodeMirrorLib from "codemirror";
    import { tick } from "svelte";
    import { writable } from "svelte/store";

    import Popover from "$lib/components/Popover.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";
    import WithOverlay from "$lib/components/WithOverlay.svelte";
    import { placeCaretAfter } from "$lib/domlib/place-caret";
    import { isComposing } from "$lib/sveltelib/composition";

    import { escapeSomeEntities, unescapeSomeEntities } from "../../editable/mathjax";
    import { Mathjax } from "../../editable/mathjax-element.svelte";
    import type { EditingInputAPI } from "../EditingArea.svelte";
    import HandleBackground from "../HandleBackground.svelte";
    import { context } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import MathjaxButtons from "./MathjaxButtons.svelte";
    import MathjaxEditor from "./MathjaxEditor.svelte";

    const { focusedInput } = context.get();

    let cleanup: Callback;
    let richTextInput: RichTextInputAPI | null = null;
    let allowPromise = Promise.resolve();

    async function initialize(input: EditingInputAPI | null): Promise<void> {
        cleanup?.();

        const isRichText = input && editingInputIsRichText(input);

        // Setup the new field, so that clicking from one mathjax to another
        // will immediately open the overlay
        if (isRichText) {
            const container = await input.element;

            cleanup = singleCallback(
                on(container, "click", showOverlayIfMathjaxClicked),
                on(container, "movecaretafter" as any, showOnAutofocus),
                on(container, "selectall" as any, showSelectAll),
            );
        }

        // Wait if the mathjax overlay is still active
        await allowPromise;

        if (!isRichText) {
            richTextInput = null;
            return;
        }

        richTextInput = input;
    }

    $: initialize($focusedInput);

    let activeImage: HTMLImageElement | null = null;
    let mathjaxElement: HTMLElement | null = null;

    let allowResubscription: Callback;
    let unsubscribe: Callback;

    let selectAll = false;
    let position: CodeMirrorLib.Position | undefined = undefined;

    /**
     * Will contain the Mathjax text with unescaped entities.
     * This is the text displayed in the actual editor window.
     */
    const code = writable("");

    function showOverlay(image: HTMLImageElement, pos?: CodeMirrorLib.Position) {
        if ($isComposing) {
            // Should be canceled while an IME composition session is active
            return;
        }

        const [promise, allowResolve] = promiseWithResolver<void>();

        allowPromise = promise;
        allowResubscription = singleCallback(
            richTextInput!.preventResubscription(),
            allowResolve,
        );

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
        richTextInput!.editable.focusHandler.flushCaret();

        if (after) {
            (mathjaxElement as any).placeCaretAfter();
        } else {
            (mathjaxElement as any).placeCaretBefore();
        }
    }

    async function resetHandle(): Promise<void> {
        selectAll = false;
        position = undefined;

        allowResubscription?.();

        if (activeImage && mathjaxElement) {
            clear();
        }
    }

    function clear(): void {
        unsubscribe();
        activeImage = null;
        mathjaxElement = null;
    }

    let errorMessage: string;
    let cleanupImageError: Callback | null = null;

    async function updateErrorMessage(): Promise<void> {
        errorMessage = activeImage!.title;
    }

    async function updateImageErrorCallback(image: HTMLImageElement | null) {
        cleanupImageError?.();
        cleanupImageError = null;

        if (!image) {
            return;
        }

        cleanupImageError = on(image, "resize", updateErrorMessage);
    }

    $: updateImageErrorCallback(activeImage);

    async function showOverlayIfMathjaxClicked({ target }: Event): Promise<void> {
        if (target instanceof HTMLImageElement && target.dataset.anki === "mathjax") {
            resetHandle();
            showOverlay(target);
        }
    }

    async function showOnAutofocus({
        detail,
    }: CustomEvent<{
        image: HTMLImageElement;
        position?: [number, number];
    }>): Promise<void> {
        let position: CodeMirrorLib.Position | undefined = undefined;

        if (detail.position) {
            const [line, ch] = detail.position;
            position = { line, ch };
        }

        showOverlay(detail.image, position);
    }

    async function showSelectAll({
        detail,
    }: CustomEvent<HTMLImageElement>): Promise<void> {
        selectAll = true;
        showOverlay(detail);
    }

    let isBlock: boolean;
    $: isBlock = mathjaxElement ? hasBlockAttribute(mathjaxElement) : false;

    async function updateBlockAttribute(): Promise<void> {
        mathjaxElement!.setAttribute("block", String(isBlock));

        // We assume that by the end of this tick, the image will have
        // adjusted its styling to either block or inline
        await tick();
    }

    const acceptShortcut = "Enter";
    const newlineShortcut = "Shift+Enter";
</script>

<div class="mathjax-overlay">
    {#if activeImage && mathjaxElement}
        <WithOverlay
            reference={activeImage}
            padding={isBlock ? 10 : 3}
            keepOnKeyup
            let:position={positionOverlay}
        >
            <WithFloating
                reference={activeImage}
                offset={20}
                keepOnKeyup
                portalTarget={document.body}
                on:close={resetHandle}
            >
                <Popover slot="floating" let:position={positionFloating}>
                    <MathjaxEditor
                        {acceptShortcut}
                        {newlineShortcut}
                        {code}
                        {selectAll}
                        {position}
                        on:moveoutstart={() => {
                            placeHandle(false);
                            resetHandle();
                        }}
                        on:moveoutend={() => {
                            placeHandle(true);
                            resetHandle();
                        }}
                        on:close={resetHandle}
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
                            on:delete={async () => {
                                if (activeImage) {
                                    placeCaretAfter(activeImage);
                                    mathjaxElement?.remove();
                                    clear();
                                }
                            }}
                            on:surround={async ({ detail }) => {
                                const editor = await mathjaxEditor.editor;
                                const { prefix, suffix } = detail;

                                editor.replaceSelection(
                                    prefix + editor.getSelection() + suffix,
                                );
                            }}
                        />
                    </MathjaxEditor>
                </Popover>
            </WithFloating>

            <svelte:fragment slot="overlay">
                <HandleBackground
                    tooltip={errorMessage}
                    --handle-background-color="var(--code-bg)"
                />
            </svelte:fragment>
        </WithOverlay>
    {/if}
</div>
