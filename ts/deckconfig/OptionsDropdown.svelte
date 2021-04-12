<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<style>
    :global(svg) {
        vertical-align: text-bottom;
    }
</style>

<div class="btn-group">
    <button type="button" class="btn btn-primary">Save</button>
    <button
        type="button"
        class="btn btn-primary dropdown-toggle dropdown-toggle-split"
        data-bs-toggle="dropdown"
        aria-expanded="false">
        <span class="visually-hidden">Toggle Dropdown</span>
    </button>
    <ul class="dropdown-menu">
        <li><a class="dropdown-item" href={'#'}>Add</a></li>
        <li><a class="dropdown-item" href={'#'}>Rename</a></li>
        <li><a class="dropdown-item" href={'#'}>Remove</a></li>
        <li>
            <hr class="dropdown-divider" />
        </li>
        <input type="checkbox" class="form-check-input" id="dropdownCheck" />
        <label class="form-check-label" for="dropdownCheck"> Apply to Children </label>
    </ul>
</div>
