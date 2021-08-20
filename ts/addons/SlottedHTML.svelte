<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    export let component;
    export let slotted;
</script>

<svelte:component this={component.component} {...component.props}>
    {@html slotted}
</svelte:component>
