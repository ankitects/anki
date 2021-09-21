<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Editable from "./Editable.svelte";
    import CustomStyles from "./CustomStyles.svelte";

    import type { StyleLinkType, StyleObject } from "./CustomStyles.svelte";
    import { getContext, onDestroy } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let content: string;
    export let focusOnMount: boolean = false;

    let shadow: ShadowRoot;

    export let editable: Editable;
    export let customStyles: CustomStyles;

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

    const mutationObserver = new MutationObserver(console.log);

    function attachMutationObserver(element: Element) {
        mutationObserver.observe(element, { childList: true });
    }

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

        editable = new Editable({
            target: shadow as any,
            props: {
                content,
                focusOnMount,
            },
        });
    }

    const nightMode = getContext(nightModeKey);

    onDestroy(() => mutationObserver.disconnect());
</script>

<div>
    <div
        use:attachMutationObserver
        use:attachShadow
        class="editable-container"
        class:night-mode={nightMode}
    />

    <slot {imageOverlayStyle} />
</div>
