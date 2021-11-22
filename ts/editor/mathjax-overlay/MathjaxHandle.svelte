<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "../../components/WithDropdown.svelte";
    import MathjaxMenu from "./MathjaxMenu.svelte";
    import { onMount, onDestroy, tick } from "svelte";
    import { writable } from "svelte/store";
    import { getRichTextInput } from "../RichTextInput.svelte";
    import { placeCaretAfter } from "../../domlib/place-caret";
    import { noop } from "../../lib/functional";
    import { on } from "../../lib/events";

    const { container, api } = getRichTextInput();

    const code = writable("");

    let activeImage: HTMLImageElement | null = null;
    let mathjaxElement: HTMLElement | null = null;
    let allow = noop;
    let unsubscribe = noop;

    const caretKeyword = "caretAfter";

    function showHandle(image: HTMLImageElement): void {
        allow = api.preventResubscription();

        activeImage = image;
        image.setAttribute(caretKeyword, "true");
        mathjaxElement = activeImage.closest("anki-mathjax")!;

        code.set(mathjaxElement.dataset.mathjax ?? "");
        unsubscribe = code.subscribe((value: string) => {
            mathjaxElement!.dataset.mathjax = value;
        });
    }

    async function clearImage(): Promise<void> {
        if (activeImage && mathjaxElement) {
            unsubscribe();
            activeImage = null;
            mathjaxElement = null;
        }

        await tick();
        container.focus();
    }

    function placeCaret(image: HTMLImageElement): void {
        placeCaretAfter(image);
        image.removeAttribute(caretKeyword);
    }

    async function resetHandle(deletes: boolean = false): Promise<void> {
        await clearImage();

        const image = container.querySelector(`[${caretKeyword}]`);
        if (image) {
            placeCaret(image as HTMLImageElement);

            if (deletes) {
                image.remove();
            }
        }

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

    onMount(() => {
        const removeClick = on(container, "click", maybeShowHandle);
        const removeFocus = on(container, "focusmathjax" as any, showAutofocusHandle);

        return () => {
            removeClick();
            removeFocus();
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

<WithDropdown
    drop="down"
    autoOpen={true}
    autoClose={false}
    distance={4}
    let:createDropdown
>
    {#if activeImage && mathjaxElement}
        <MathjaxMenu
            {activeImage}
            {mathjaxElement}
            {container}
            {errorMessage}
            {code}
            bind:updateSelection
            on:mount={(event) => (dropdownApi = createDropdown(event.detail.selection))}
            on:reset={() => resetHandle(false)}
            on:delete={() => resetHandle(true)}
        />
    {/if}
</WithDropdown>
