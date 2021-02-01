<script lang="typescript">
    let fillEmptyFields: boolean;
    let nightMode: boolean;
    let addMobileClass: boolean;
</script>

<style>
    .checkbox-item input {
        margin: 0px 5px;
        top: 2px;
        position: relative;
    }

    .checkbox-item label {
        display: block;
        padding: 3px 10px;
        clear: both;
        font-weight: normal;
        line-height: 1.42857143;
        color: #333;
        white-space: nowrap;
        margin:0;
        user-select: none;
    }

    .checkbox-item:active label {
        background-color: #cbcbff;
    }

    .checkbox-item label:hover,
    .checkbox-item label:focus {
        background-color: #f5f5f5;
    }

    .checkbox-item:active label:hover,
    .checkbox-item:active label:focus {
        background-color: #b8b8ff;
    }
</style>

<div class="dropup">
    <button class="btn btn-secondary dropdown-toggle" type="button" id="dropdownMenuButton" data-bs-toggle="dropdown" aria-expanded="false">
        <i class="bi bi-gear-fill"></i>
    </button>
    <ul class="dropdown-menu dropdown-menu-end" on:click|stopPropagation>
        <li class="checkbox-item">
            <label for="fillEmptyFields">
                <input type="checkbox" class="btn-check" id="fillEmptyFields" autocomplete="off" bind={fillEmptyFields}>
                <span>Fill empty fields</span>
            </label>
        </li>
        <li class="checkbox-item">
            <label for="nightMode">
                <input type="checkbox" class="btn-check" id="nightMode" autocomplete="off" bind={nightMode}>
                <span>Night mode</span>
            </label>
        </li>
        <li class="checkbox-item">
            <label for="addMobileClass">
                <input type="checkbox" class="btn-check" id="addMobileClass" autocomplete="off" bind={addMobileClass}>
                <span>Add mobile class</span>
            </label>
        </li>
    </ul>
</div>
