<div>
    <slot />
</div>

<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<style lang="scss">
    div {
        display: flex;
        justify-content: center;

        @media only screen and (max-device-width: 480px) and (orientation: portrait) {
            font-size: smaller;
        }

        & > :global(*) {
            padding-left: 0.5em;
            padding-right: 0.5em;
        }
    }
</style>
