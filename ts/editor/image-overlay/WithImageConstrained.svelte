<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onDestroy } from "svelte";
    import { nodeIsElement } from "lib/dom";

    export let activeImage: HTMLImageElement | null;
    export let container: HTMLElement;
    export let sheet: CSSStyleSheet;

    let active: boolean = false;

    $: {
        const index = images.indexOf(activeImage!);

        if (index >= 0) {
            const rule = sheet.cssRules[index] as CSSStyleRule;
            active = rule.cssText.endsWith("{ }");
        } else {
            activeImage = null;
        }
    }

    export let maxWidth: number;
    export let maxHeight: number;

    $: restrictionAspectRatio = maxWidth / maxHeight;

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

    const images: HTMLImageElement[] = [];

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

            if (node.tagName === "IMG" && !(node as HTMLElement).dataset.anki) {
                result.push(node as HTMLImageElement);
            } else {
                result.push(...filterImages(node.children));
            }
        }

        return result;
    }

    function setImageRule(image: HTMLImageElement, rule: CSSStyleRule): void {
        const aspectRatio = image.naturalWidth / image.naturalHeight;

        if (restrictionAspectRatio - aspectRatio > 1) {
            // restricted by height
            rule.style.setProperty("width", "auto", "important");

            const width = Number(image.getAttribute("width")) || image.width;
            const height = Number(image.getAttribute("height")) || width / aspectRatio;
            rule.style.setProperty(
                "height",
                height < maxHeight ? `${height}px` : "auto",
                "important"
            );
        } else {
            // square or restricted by width
            const width = Number(image.getAttribute("width")) || image.width;
            rule.style.setProperty(
                "width",
                width < maxWidth ? `${width}px` : "auto",
                "important"
            );

            rule.style.setProperty("height", "auto", "important");
        }

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
    }

    function addImageOnLoad(image: HTMLImageElement): void {
        if (image.complete && image.naturalWidth > 0 && image.naturalHeight > 0) {
            addImage(image);
        } else {
            image.addEventListener("load", () => addImage(image));
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
    });

    mutationObserver.observe(container, {
        childList: true,
        subtree: true,
    });

    for (const image of filterImages([...container.childNodes])) {
        addImageOnLoad(image);
    }

    onDestroy(() => mutationObserver.disconnect());

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
</script>

{#if activeImage}
    <slot {toggleActualSize} {active} />
{/if}
