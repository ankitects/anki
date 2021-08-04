<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div>
    <slot />
</div>

<style lang="scss">
    div {
        position: absolute;

        left: var(--left, 0);
        top: var(--top, 0);
        width: var(--width);
        height: var(--height);
    }
</style>
