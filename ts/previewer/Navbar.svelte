<script lang="typescript">
    let showFront = false;
    let fillEmptyFields = false;
    let nightMode = false;
    let addMobileClass = false;
</script>
<style>
    .checkbox-menu li input {
        margin: 0px 5px;
        top: 2px;
        position: relative;
    }

    .checkbox-menu li label {
        display: block;
        padding: 3px 10px;
        clear: both;
        font-weight: normal;
        line-height: 1.42857143;
        color: #333;
        white-space: nowrap;
        margin:0;
    }

    .checkbox-menu li:active label {
        background-color: #cbcbff;
    }

    .checkbox-menu li label:hover,
    .checkbox-menu li label:focus {
        background-color: #f5f5f5;
    }

    .checkbox-menu li:active label:hover,
    .checkbox-menu li:active label:focus {
        background-color: #b8b8ff;
    }
</style>

<nav class="navbar navbar-light bg-light fixed-bottom">
    <div class="w-100 mx-2 d-flex justify-content-between">
        <!-- left buttons -->
        <div class="btn-group" role="group">
            <label class="btn btn-outline-primary" for="front">
                <input type="radio" class="btn-check" name="cardFormat" id="front" autocomplete="off" bind:group={showFront} value={true}>
                <span>Front</span>
            </label>

            <label class="btn btn-outline-primary" for="back">
                <input type="radio" class="btn-check" name="cardFormat" id="back" autocomplete="off" bind:group={showFront} value={false}>
                <span>Back</span>
            </label>
        </div>

        <!-- right buttons -->
        <div>
            <div class="dropup">
                <button class="btn btn-secondary dropdown-toggle" type="button" id="dropdownMenuButton" data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="bi bi-gear-fill"></i>
                </button>
                <ul class="dropdown-menu dropdown-menu-end checkbox-menu" on:click|stopPropagation>
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
        </div>
    </div>
</nav>
