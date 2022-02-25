<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { promiseWithResolver } from "../../lib/promise";
    import { context as editorFieldContext } from "../EditorField.svelte";
    import type { StyleLinkType, StyleObject } from "./CustomStyles.svelte";
    import CustomStyles from "./CustomStyles.svelte";

    const [promise, customStylesResolve] = promiseWithResolver<CustomStyles>();
    const [userBaseStyle, userBaseResolve] = promiseWithResolver<StyleObject>();
    const [userBaseRule, userBaseRuleResolve] = promiseWithResolver<CSSStyleRule>();

    const stylesDidLoad: Promise<unknown> = Promise.all([
        promise,
        userBaseStyle,
        userBaseRule,
    ]);

    userBaseStyle.then((baseStyle: StyleObject) => {
        const sheet = baseStyle.element.sheet as CSSStyleSheet;
        const baseIndex = sheet.insertRule("anki-editable {}");
        userBaseRuleResolve(sheet.cssRules[baseIndex] as CSSStyleRule);
    });

    export let color: string;

    const { field } = editorFieldContext.get();

    let fontFamily: string;
    let fontSize: number;
    let rtl: boolean;
    let direction: "ltr" | "rtl";

    $: ({ fontName: fontFamily, fontSize, rtl } = $field.config!);
    $: direction = rtl ? "rtl" : "ltr";

    function quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    async function setStyling(property: string, value: unknown): Promise<void> {
        const rule = await userBaseRule;
        rule.style[property] = value;
    }

    $: setStyling("color", color);
    $: setStyling("fontFamily", quoteFontFamily(fontFamily));
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

        customStyles.addStyleTag("userBase").then(userBaseResolve);
        customStylesResolve(customStyles);
    }
</script>

<slot {attachToShadow} {promise} {stylesDidLoad} />
