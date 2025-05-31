<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { promiseWithResolver } from "@tslib/promise";

    import type { StyleLinkType, StyleObject } from "./CustomStyles.svelte";
    import CustomStyles from "./CustomStyles.svelte";
    import editableBaseCSS from "$lib/editable/editable-base.scss?url";
    import contentEditableCSS from "$lib/editable/content-editable.scss?url";
    import mathjaxCSS from "$lib/editable/mathjax.scss?url";

    import { mount } from "svelte";

    export let callback: (styles: Record<string, any>) => void;

    const [userBaseStyle, userBaseResolve] = promiseWithResolver<StyleObject>();
    const [userBaseRule, userBaseRuleResolve] = promiseWithResolver<CSSStyleRule>();

    const stylesDidLoad: Promise<unknown> = Promise.all([userBaseStyle, userBaseRule]);

    userBaseStyle.then((baseStyle: StyleObject) => {
        const sheet = baseStyle.element.sheet as CSSStyleSheet;
        const baseIndex = sheet.insertRule("anki-editable {}");
        userBaseRuleResolve(sheet.cssRules[baseIndex] as CSSStyleRule);
    });

    export let color: string;
    export let fontFamily: string;
    export let fontSize: number;
    export let direction: "ltr" | "rtl";

    async function setStyling(property: string, value: unknown): Promise<void> {
        const rule = await userBaseRule;
        rule.style[property] = value;

        // if we don't set the textContent of the underlying HTMLStyleElement, addons
        // which extend the custom style and set textContent of their registered tags
        // will cause the userBase style tag here to be ignored
        const baseStyle = await userBaseStyle;
        baseStyle.element.textContent = rule.cssText;
    }

    $: setStyling("color", color);
    $: setStyling("fontFamily", fontFamily);
    $: setStyling("fontSize", fontSize + "px");
    $: setStyling("direction", direction);

    const styles: StyleLinkType[] = [
        {
            id: "editableBaseStyle",
            type: "link",
            href: editableBaseCSS,
        },
        {
            id: "contentEditableStyle",
            type: "link",
            href: contentEditableCSS,
        },
        {
            id: "mathjaxStyle",
            type: "link",
            href: mathjaxCSS,
        },
    ];

    function attachToShadow(element: Element) {
        const customStyles = mount(CustomStyles, {
            target: element.shadowRoot!,
            props: { styles },
        });
        customStyles.addStyleTag("userBase").then((styleTag) => {
            userBaseResolve(styleTag);
            callback(customStyles);
        });
    }
</script>

<slot {attachToShadow} {stylesDidLoad} />
