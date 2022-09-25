<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let size: number | undefined = undefined;
    export let wrap: boolean | undefined = undefined;

    console.log(wrap);
</script>

<div
    {id}
    class="button-group {className}"
    dir="ltr"
    style:--button-size="{size}rem"
    role="group"
>
    <slot />
</div>

<style lang="scss">
    @use "sass/vars" as vars;

    .button-group {
        border: 1px solid var(--button-border);
        border-radius: var(--border-radius);

        display: grid;
        grid-auto-flow: column;
        grid-gap: 1px;

        background-color: var(--button-border);
        overflow: hidden;
    }
</style>
