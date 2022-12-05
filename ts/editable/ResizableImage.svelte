<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<style lang="scss">
    :global(img:not(.mathjax)) {
        max-width: var(--editor-default-max-width);
        max-height: var(--editor-default-max-height);

        &:is([data-editor-shrink="true"]) {
            max-width: var(--editor-shrink-max-width);
            max-height: var(--editor-shrink-max-height);
        }
        &:is([data-editor-shrink="false"]) {
            max-width: initial;
            max-height: initial;
        }
    }
</style>
