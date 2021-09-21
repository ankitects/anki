<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    interface FieldObject {
    }
</script>

<script lang="ts">
    import { setContext } from "svelte";
    import type { FieldData } from "./adapter-types";
    import { fieldsKey } from "lib/context-keys";

    export let fields: FieldData[];
    export let focusTo: number;

    let className: string = "";
    export { className as class };

    export const fieldsList: FieldObject[] = [];

    function register(index: string, object: FieldObject): void {
        fieldsList[index] = object;
    }

    function deregister(index: string): void {
        delete fieldsList[index];
    }

    setContext(fieldsKey, { register, deregister });
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
