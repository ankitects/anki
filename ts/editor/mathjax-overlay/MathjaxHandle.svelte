<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type CodeMirrorLib from "codemirror";
    import { onDestroy, onMount, tick } from "svelte";
    import { writable } from "svelte/store";

    import WithFloating from "../../components/WithFloating.svelte";
    import WithOverlay from "../../components/WithOverlay.svelte";
    import { escapeSomeEntities, unescapeSomeEntities } from "../../editable/mathjax";
    import { Mathjax } from "../../editable/mathjax-element";
    import { on } from "../../lib/events";
    import { noop } from "../../lib/functional";
    import type { Callback } from "../../lib/typing";
    import { singleCallback } from "../../lib/typing";
    import type { ResizeStore } from "../../sveltelib/resize-store"
    import resizeStore from "../../sveltelib/resize-store"
    import subscribeToUpdates from "../../sveltelib/subscribe-updates"
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import { context } from "../rich-text-input";
    import MathjaxMenu from "./MathjaxMenu.svelte";

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
</script>


{#if activeImage && mathjaxElement}
    <WithOverlay
        reference={activeImage}
        padding={8}
        keepOnKeyup
        let:position={doPositionOverlay}
    >
        <WithFloating
            reference={activeImage}
            placement="auto"
            offset={20}
            keepOnKeyup
            hideIfEscaped
            let:position={doPositionFloating}
        >
            <MathjaxMenu
                slot="floating"
                element={mathjaxElement}
                {code}
                {selectAll}
                {position}
                on:reset={resetHandle}
                on:resize={() => {
                    doPositionFloating((reference, floating, position) => position(reference, floating));
                    doPositionOverlay((reference, floating, position) => position(reference, floating));
                }}
                on:moveoutstart={() => {
                    placeHandle(false);
                    resetHandle();
                }}
                on:moveoutend={() => {
                    placeHandle(true);
                    resetHandle();
                }}
            />
        </WithFloating>

        <svelte:fragment slot="overlay">
            <HandleBackground tooltip={errorMessage} />
        </svelte:fragment>
    </WithOverlay>
{/if}

