<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let id: string | undefined = undefined;
    let className: string | undefined;
    export { className as class };

    export let nowrap = false;
</script>

<div {id} class={`btn-toolbar ${className}`} class:flex-nowrap={nowrap} role="toolbar">
    <slot />
</div>
