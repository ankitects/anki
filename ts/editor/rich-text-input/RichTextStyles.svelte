<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { promiseWithResolver } from "@tslib/promise";

    import type { StyleLinkType, StyleObject } from "./CustomStyles.svelte";
    import CustomStyles from "./CustomStyles.svelte";

    export let callback: (styles: CustomStyles) => void;

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
    }

    $: setStyling("color", color);
    $: setStyling("fontFamily", fontFamily);
    $: setStyling("fontSize", fontSize + "px");
    $: setStyling("direction", direction);

    const styles = [
        {
            id: "rootStyle",
            type: "link" as "link",
            href: "./_anki/css/editable.css",
        } as StyleLinkType,
    ];

    function attachToShadow(element: Element) {
        const customStyles = new CustomStyles({
            target: element.shadowRoot as any,
            props: { styles },
        });

        customStyles.addStyleTag("userBase").then((styleTag) => {
            userBaseResolve(styleTag);
            callback(customStyles);
        });
    }
</script>

<slot {attachToShadow} {stylesDidLoad} />
