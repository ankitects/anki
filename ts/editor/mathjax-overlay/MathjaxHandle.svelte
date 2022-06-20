<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type CodeMirrorLib from "codemirror";
    import { onDestroy, onMount, tick } from "svelte";
    import { writable } from "svelte/store";

    import WithDropdown from "../../components/WithDropdown.svelte";
    import { escapeSomeEntities, unescapeSomeEntities } from "../../editable/mathjax";
    import { Mathjax } from "../../editable/mathjax-element";
    import { on } from "../../lib/events";
    import { noop } from "../../lib/functional";
    import { singleCallback } from "../../lib/typing";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import HandleSelection from "../HandleSelection.svelte";
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

    async function maybeShowHandle({ target }: Event): Promise<void> {
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
            on(container, "click", maybeShowHandle),
            on(container, "movecaretafter" as any, showAutofocusHandle),
            on(container, "selectall" as any, showSelectAll),
        );
    });

    let updateSelection: () => Promise<void>;
    let errorMessage: string;
    let dropdownApi: any;

    async function onImageResize(): Promise<void> {
        errorMessage = activeImage!.title;
        await updateSelection();
        dropdownApi.update();
    }

    const resizeObserver = new ResizeObserver(onImageResize);

    let clearResize = noop;
    async function handleImageResizing(activeImage: HTMLImageElement | null) {
        const container = await element;

        if (activeImage) {
            resizeObserver.observe(container);
            clearResize = on(activeImage, "resize", onImageResize);
        } else {
            resizeObserver.unobserve(container);
            clearResize();
        }
    }

    $: handleImageResizing(activeImage);

    onDestroy(() => {
        resizeObserver.disconnect();
        clearResize();
    });
</script>

<WithDropdown drop="down" autoOpen autoClose={false} distance={4} let:createDropdown>
    {#if activeImage && mathjaxElement}
        <MathjaxMenu
            element={mathjaxElement}
            {code}
            {selectAll}
            {position}
            bind:updateSelection
            on:reset={resetHandle}
            on:moveoutstart={() => {
                placeHandle(false);
                resetHandle();
            }}
            on:moveoutend={() => {
                placeHandle(true);
                resetHandle();
            }}
        >
            {#await element then container}
                <HandleSelection
                    image={activeImage}
                    {container}
                    bind:updateSelection
                    on:mount={(event) =>
                        (dropdownApi = createDropdown(event.detail.selection))}
                >
                    <HandleBackground tooltip={errorMessage} />
                    <HandleControl offsetX={1} offsetY={1} />
                </HandleSelection>
            {/await}
        </MathjaxMenu>
    {/if}
</WithDropdown>
