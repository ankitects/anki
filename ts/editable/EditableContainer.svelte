<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Editable from "./Editable.svelte";
    import CustomStyles from "./CustomStyles.svelte";

    import type { StyleLinkType, StyleObject } from "./CustomStyles.svelte";
    import { getContext, getAllContexts } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let content: string;
    export let focusOnMount: boolean = false;

    $: {
        content;
        console.log("editablecontainer", content);
    }

    let shadow: ShadowRoot;

    let editableResolve: (editable: Editable) => void;
    const editablePromise: Promise<Editable> = new Promise(
        (resolve) => (editableResolve = resolve)
    );

    $: editablePromise.then((editable) => editable.$set({ content }));

    let customStyles: CustomStyles;

    let userBaseResolve: (object: StyleObject) => void;
    export const userBaseStyle = new Promise<StyleObject>(
        (resolve) => (userBaseResolve = resolve)
    );

    let userBaseRuleResolve: (rule: CSSStyleRule) => void;
    export const userBaseRule = new Promise<CSSStyleRule>(
        (resolve) => (userBaseRuleResolve = resolve)
    );

    let imageOverlayResolve: (object: StyleObject) => void;
    export const imageOverlayStyle = new Promise<StyleObject>(
        (resolve) => (imageOverlayResolve = resolve)
    );

    export let color: string;
    export let fontName: string;
    export let fontSize: string;
    export let direction: string;

    $: userBaseRule.then((rule) => (rule.style.color = color));
    $: userBaseRule.then((rule) => (rule.style.fontFamily = fontName));
    $: userBaseRule.then((rule) => (rule.style.fontSize = `${fontSize}px`));
    $: userBaseRule.then((rule) => (rule.style.direction = direction));

    /* isRightToLeft(): boolean { */
    /*     return this.baseRule!.style.direction === "rtl"; */
    /* } */

    const allContexts = getAllContexts();

    function attachShadow(element: Element) {
        shadow = element.attachShadow({ mode: "open" });

        const styles = [
            {
                id: "rootStyle",
                type: "link" as "link",
                href: "./_anki/css/editable-build.css",
            } as StyleLinkType,
        ];

        customStyles = new CustomStyles({
            target: shadow as any,
            props: { styles },
        });

        customStyles.addStyleTag("userBase").then(userBaseResolve);

        userBaseStyle.then((baseStyle) => {
            const sheet = baseStyle.element.sheet as CSSStyleSheet;
            const baseIndex = sheet.insertRule("anki-editable {}");
            userBaseRuleResolve(sheet.cssRules[baseIndex] as CSSStyleRule);
        });

        customStyles.addStyleTag("imageOverlay").then(imageOverlayResolve);

        // make component initiation asynchronous
        // https://github.com/sveltejs/svelte/issues/6753#issuecomment-924344561
        setTimeout(() =>
            editableResolve(
                new Editable({
                    target: shadow as any,
                    props: {
                        content,
                        focusOnMount,
                    },
                    context: allContexts,
                } as any)
            )
        );
    }

    const nightMode = getContext(nightModeKey);

    let overlayRelative: HTMLElement;
</script>

<div bind:this={overlayRelative} class="overlay-relative">
    <div use:attachShadow class="editable-container" class:night-mode={nightMode} />

    <!-- slot for overlays -->
    {#if overlayRelative}
        <slot
            {overlayRelative}
            imageOverlaySheet={imageOverlayStyle.then((object) => object.element.sheet)}
        />
    {/if}
</div>

<style lang="scss">
    .overlay-relative {
        position: relative;
    }
</style>
