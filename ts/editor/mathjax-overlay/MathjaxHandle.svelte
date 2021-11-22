<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "../../components/WithDropdown.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import Item from "../../components/Item.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import HandleSelection from "../HandleSelection.svelte";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import InlineBlock from "./InlineBlock.svelte";
    import CodeMirror from "../CodeMirror.svelte";

    import { onMount, onDestroy, tick } from "svelte";
    import { writable } from "svelte/store";
    import { getRichTextInput } from "../RichTextInput.svelte";
    import { baseOptions, latex } from "../code-mirror";
    import { getPlatformString } from "../../lib/shortcuts";
    import { placeCaretAfter } from "../../domlib/place-caret";
    import { noop } from "../../lib/functional";
    import { on } from "../../lib/events";
    import * as tr from "../../lib/ftl";

    const { container, api } = getRichTextInput();

    const acceptShortcut = "Enter";
    const newlineShortcut = "Shift+Enter";

    const configuration = {
        ...Object.assign({}, baseOptions, {
            extraKeys: {
                ...(baseOptions.extraKeys as CodeMirror.KeyMap),
                [acceptShortcut]: noop,
                [newlineShortcut]: noop,
            },
        }),
        placeholder: tr.editingMathjaxPlaceholder({
            accept: getPlatformString(acceptShortcut),
            newline: getPlatformString(newlineShortcut),
        }),
        mode: latex,
    };

    let activeImage: HTMLImageElement | null = null;
    let allow = noop;

    const code = writable("");

    function appendNewline(): void {
        code.update((value) => `${value}foo\n`);
    }

    let unsubscribe = noop;
    let ankiMathjax: HTMLElement | null = null;

    function showHandle(image: HTMLImageElement): void {
        allow = api.preventResubscription();

        activeImage = image;
        image.setAttribute("caretafter", "true");
        ankiMathjax = activeImage.closest("anki-mathjax")!;

        code.set(ankiMathjax.dataset.mathjax ?? "");
        unsubscribe = code.subscribe((value: string) => {
            ankiMathjax!.dataset.mathjax = value;
        });
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

    async function resetHandle(): Promise<void> {
        if (activeImage && ankiMathjax) {
            unsubscribe();
            activeImage = null;
        }

        await tick();
        const image = container.querySelector("[caretafter]");

        if (image) {
            placeCaretAfter(image);
            image.removeAttribute("caretafter");
        }

        allow();
        allow = noop;
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
    {#if activeImage}
        <HandleSelection
            image={activeImage}
            {container}
            bind:updateSelection
            on:mount={(event) => (dropdownApi = createDropdown(event.detail.selection))}
        >
            <HandleBackground tooltip={errorMessage} />
            <HandleControl offsetX={1} offsetY={1} />
        </HandleSelection>

        <Shortcut keyCombination={acceptShortcut} on:action={resetHandle} />

        <Shortcut keyCombination={newlineShortcut} on:action={appendNewline} />

        <DropdownMenu>
            <div class="mathjax-editor">
                <CodeMirror
                    {code}
                    {configuration}
                    on:change={({ detail }) => code.set(detail)}
                    on:blur={resetHandle}
                    autofocus
                />
            </div>
            <ButtonToolbar>
                <Item>
                    <InlineBlock {activeImage} on:click={updateSelection} />
                </Item>
            </ButtonToolbar>
        </DropdownMenu>
    {/if}
</WithDropdown>

<style lang="scss">
    .mathjax-editor {
        :global(.CodeMirror) {
            max-width: 28rem;
            margin-bottom: 0.25rem;
        }

        :global(.CodeMirror-placeholder) {
            font-family: sans-serif;
            font-size: 55%;
            text-align: center;
            color: var(--slightly-grey-text);
        }
    }
</style>
