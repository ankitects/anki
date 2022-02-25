<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onDestroy, onMount, tick } from "svelte";
    import { writable } from "svelte/store";

    import WithDropdown from "../../components/WithDropdown.svelte";
    import { Mathjax } from "../../editable/mathjax-element";
    import { on } from "../../lib/events";
    import { noop } from "../../lib/functional";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import HandleSelection from "../HandleSelection.svelte";
    import { context } from "../rich-text-input";
    import MathjaxMenu from "./MathjaxMenu.svelte";

    const { container, api } = context.get();
    const { focusHandler, preventResubscription } = api;

    const code = writable("");

    let activeImage: HTMLImageElement | null = null;
    let mathjaxElement: HTMLElement | null = null;
    let allow = noop;
    let unsubscribe = noop;

    function showHandle(image: HTMLImageElement): void {
        allow = preventResubscription();

        activeImage = image;
        mathjaxElement = activeImage.closest(Mathjax.tagName)!;

        code.set(mathjaxElement.dataset.mathjax ?? "");
        unsubscribe = code.subscribe((value: string) => {
            mathjaxElement!.dataset.mathjax = value;
        });
    }

    let selectAll = false;

    function placeHandle(after: boolean): void {
        focusHandler.flushCaret();

        if (after) {
            (mathjaxElement as any).placeCaretAfter();
        } else {
            (mathjaxElement as any).placeCaretBefore();
        }
    }

    async function resetHandle(): Promise<void> {
        selectAll = false;

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
    }: CustomEvent<HTMLImageElement>): Promise<void> {
        await resetHandle();
        showHandle(detail);
    }

    async function showSelectAll({
        detail,
    }: CustomEvent<HTMLImageElement>): Promise<void> {
        await resetHandle();
        selectAll = true;
        showHandle(detail);
    }

    onMount(() => {
        const removeClick = on(container, "click", maybeShowHandle);
        const removeCaretAfter = on(
            container,
            "movecaretafter" as any,
            showAutofocusHandle,
        );
        const removeSelectAll = on(container, "selectall" as any, showSelectAll);

        return () => {
            removeClick();
            removeCaretAfter();
            removeSelectAll();
        };
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
    function handleImageResizing(activeImage: HTMLImageElement | null) {
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
        </MathjaxMenu>
    {/if}
</WithDropdown>
