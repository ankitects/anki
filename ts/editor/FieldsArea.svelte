<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { FieldData } from "./adapter-types";

    export let fields: FieldData[];
    export let focusTo: number;

    let className: string = "";
    export { className as class };
</script>

<main class="fields-editor {className}">
    {#each fields as field, index}
        <slot
            {field}
            ord={index}
            focusOnMount={index === focusTo}
            fieldName={field.fieldName}
            content={field.fieldContent}
            fontName={field.fontName}
            fontSize={field.fontSize}
            direction={field.rtl ? "rtl" : "ltr"}
        />
    {/each}
</main>

<style lang="scss">
    .fields-editor {
        display: flex;
        flex-direction: column;

        overflow-x: hidden;
        padding: 3px 0;
    }
</style>
