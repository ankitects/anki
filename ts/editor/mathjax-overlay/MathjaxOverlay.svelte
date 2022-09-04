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

    let show = true;
    let selectAll = false;
    let position: CodeMirrorLib.Position | undefined = undefined;

    /**
     * Will contain the Mathjax text with unescaped entities.
     * This is the text displayed in the actual editor window.
     */
    const code = writable("");

    function showOverlay(image: HTMLImageElement, pos?: CodeMirrorLib.Position): void {
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

        show = true;
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

        allow();

        // Wait for a tick, so that moving from one Mathjax element to
        // another will remount the MathjaxEditor
        await tick();
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

    async function showOverlayIfMathjaxClicked({ target }: Event): Promise<void> {
        if (target instanceof HTMLImageElement && target.dataset.anki === "mathjax") {
            await resetHandle();
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

    onMount(async () => {
        const container = await element;

        return singleCallback(
            on(container, "click", showOverlayIfMathjaxClicked),
            on(container, "movecaretafter" as any, showOnAutofocus),
            on(container, "selectall" as any, showSelectAll),
        );
    });

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

{#if activeImage && mathjaxElement}
    <WithOverlay
        reference={activeImage}
        {show}
        padding={isBlock ? 10 : 3}
        keepOnKeyup
        let:position={positionOverlay}
    >
        <WithFloating
            reference={activeImage}
            {show}
            placement="auto"
            offset={20}
            keepOnKeyup
            hideIfEscaped
            let:position={positionFloating}
            on:close={resetHandle}
        >
            <Popover slot="floating">
                <MathjaxEditor
                    {acceptShortcut}
                    {newlineShortcut}
                    {code}
                    {selectAll}
                    {position}
                    on:moveoutstart={async () => {
                        placeHandle(false);
                        await resetHandle();
                    }}
                    on:moveoutend={async () => {
                        placeHandle(true);
                        await resetHandle();
                    }}
                    on:tab={async () => {
                        // Instead of resetting on blur, we reset on tab
                        // Otherwise, when clicking from Mathjax element to another,
                        // the user has to click twice (focus is called before blur?)
                        await resetHandle();
                    }}
                    let:editor={mathjaxEditor}
                >
                    <Shortcut
                        keyCombination={acceptShortcut}
                        on:action={async () => {
                            placeHandle(true);
                            await resetHandle();
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
                            placeCaretAfter(activeImage);
                            activeImage.remove();
                            await resetHandle();
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
            <HandleBackground tooltip={errorMessage} />
        </svelte:fragment>
    </WithOverlay>
{/if}
