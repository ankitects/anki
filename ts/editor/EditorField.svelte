<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="editor-field">
    <slot />
</div>

<style lang="scss">
    .editor-field {
        margin: 3px;

        border-radius: 5px;
        border: 1px solid var(--border-color);

        --border-color: var(--border);

        &:focus-within {
            box-shadow: 0 0 0 3px var(--focus-shadow);

            --border-color: var(--focus-border);
        }
    }
</style>
