<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";

    import { createEventDispatcher, onDestroy } from "svelte";
    import { sizeActual, sizeMinimized } from "./icons";
    import { nodeIsElement } from "./helpers";

    export let container: HTMLElement;
    export let sheet: CSSStyleSheet;

    export let activeImage: HTMLImageElement | null;
    export let active: boolean = false;

    $: {
        const index = images.indexOf(activeImage!);

        if (index >= 0) {
            const rule = sheet.cssRules[index] as CSSStyleRule;
            active = rule.cssText.endsWith("{ }");
        }
    }

    export let isRtl: boolean;
    export let maxWidth = 250;
    export let maxHeight = 125;

    $: icon = active ? sizeActual : sizeMinimized;

    const dispatch = createEventDispatcher();

    function createPathRecursive(tokens: string[], element: Element): string[] {
        const tagName = element.tagName.toLowerCase();

        if (!element.parentElement) {
            const nth =
                Array.prototype.indexOf.call(
                    (element.parentNode! as Document | ShadowRoot).children,
                    element
                ) + 1;
            return [`${tagName}:nth-child(${nth})`, ...tokens];
        } else {
            const nth =
                Array.prototype.indexOf.call(element.parentElement.children, element) +
                1;
            return createPathRecursive(
                [`${tagName}:nth-child(${nth})`, ...tokens],
                element.parentElement
            );
        }
    }

    function createPath(element: Element): string {
        return createPathRecursive([], element).join(" > ");
    }

    let images: HTMLImageElement[] = [];

    $: for (const [index, image] of images.entries()) {
        const rule = sheet.cssRules[index] as CSSStyleRule;
        rule.selectorText = createPath(image);
    }

    function filterImages(nodes: HTMLCollection | Node[]): HTMLImageElement[] {
        const result: HTMLImageElement[] = [];

        for (const node of nodes) {
            if (!nodeIsElement(node)) {
                continue;
            }

            if (node.tagName === "IMG") {
                result.push(node as HTMLImageElement);
            } else {
                result.push(...filterImages(node.children));
            }
        }

        return result;
    }

    function setImageRule(image: HTMLImageElement, rule: CSSStyleRule): void {
        const width = Number(image.getAttribute("width")) | image.width;

        rule.style.setProperty(
            "width",
            width < maxWidth ? `${width}px` : "auto",
            "important"
        );
        rule.style.setProperty("height", "auto", "important");
        rule.style.setProperty("max-width", `min(${maxWidth}px, 100%)`, "important");
        rule.style.setProperty("max-height", `${maxHeight}px`, "important");
    }

    function resetImageRule(rule: CSSStyleRule): void {
        rule.style.removeProperty("width");
        rule.style.removeProperty("height");
        rule.style.removeProperty("max-width");
        rule.style.removeProperty("max-height");
    }

    function addImage(image: HTMLImageElement): void {
        if (!container.contains(image)) {
            return;
        }

        images.push(image);
        const index = sheet.insertRule(
            `${createPath(image)} {}`,
            sheet.cssRules.length
        );
        const rule = sheet.cssRules[index] as CSSStyleRule;
        setImageRule(image, rule);

        images = images;
    }

    function addImageOnLoad(image: HTMLImageElement): void {
        if (image.complete && image.naturalWidth !== 0 && image.naturalHeight !== 0) {
            addImage(image);
        } else {
            image.addEventListener("load", () => {
                addImage(image);
            });
        }
    }

    function removeImage(image: HTMLImageElement): void {
        const index = images.indexOf(image);
        if (index < 0) {
            return;
        }

        images.splice(index, 1);
        sheet.deleteRule(index);
    }

    const mutationObserver = new MutationObserver((mutations) => {
        const addedImages = mutations.flatMap((mutation) =>
            filterImages([...mutation.addedNodes])
        );

        for (const image of addedImages) {
            addImageOnLoad(image);
        }

        const removedImages = mutations.flatMap((mutation) =>
            filterImages([...mutation.removedNodes])
        );

        for (const image of removedImages) {
            removeImage(image);
        }

        images = images;
    });

    $: if (container) {
        mutationObserver.observe(container, { childList: true, subtree: true });
    }

    export function toggleActualSize() {
        const index = images.indexOf(activeImage!);
        if (index === -1) {
            return;
        }

        const rule = sheet.cssRules[index] as CSSStyleRule;
        active = !active;

        if (active) {
            resetImageRule(rule);
        } else {
            setImageRule(activeImage!, rule);
        }

        dispatch("update", active);
    }

    onDestroy(() => mutationObserver.disconnect());
</script>

{#if activeImage}
    <ButtonGroup size={1.6}>
        <ButtonGroupItem>
            <IconButton
                {active}
                flipX={isRtl}
                tooltip={tr.editingActualSize()}
                on:click={toggleActualSize}>{@html icon}</IconButton
            >
        </ButtonGroupItem>
    </ButtonGroup>
{/if}
