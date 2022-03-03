<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="drawer-item">
    <slot />
</div>

<style lang="scss">
    .drawer-item {
        position: relative;

        display: flex;
        flex-direction: row;

        padding: 0 5px;
        height: 22px;
        min-width: 0;
        cursor: pointer;

        animation: scaleIn 0.1s linear;
    }

    @keyframes scaleIn {
        0% {
            transform: translateY(-3px);
        }
        100% {
            transform: translate(0);
        }
    }

    .drawer-item:hover {
        background-color: pink;
    }
</style>
