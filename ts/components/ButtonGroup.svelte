<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let size = -1;
    export let wrap = false;

    let buttonWrap: string;
    $: buttonWrap = wrap ? `wrap` : `nowrap`;
</script>

<div
    {id}
    class="button-group btn-group {className}"
    style:--button-size="{size}rem"
    style:--button-wrap="{buttonWrap}rem"
    dir="ltr"
    role="group"
>
    <slot />
</div>

<style lang="scss">
    .button-group {
        display: flex;
        flex-flow: row var(--buttons-wrap);
    }
</style>
