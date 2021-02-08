import { EditingArea } from ".";

export function updateButtonState(): void {
    const buts = ["bold", "italic", "underline", "superscript", "subscript"];
    for (const name of buts) {
        const elem = document.querySelector(`#${name}`) as HTMLElement;
        elem.classList.toggle("highlighted", document.queryCommandState(name));
    }

    // fixme: forecolor
    //    'col': document.queryCommandValue("forecolor")
}

export function preventButtonFocus(): void {
    for (const element of document.querySelectorAll("button.linkb")) {
        element.addEventListener("mousedown", (evt: Event) => {
            evt.preventDefault();
        });
    }
}

export function disableButtons(): void {
    $("button.linkb:not(.perm)").prop("disabled", true);
}

export function enableButtons(): void {
    $("button.linkb").prop("disabled", false);
}

// disable the buttons if a field is not currently focused
export function maybeDisableButtons(): void {
    if (document.activeElement instanceof EditingArea) {
        enableButtons();
    } else {
        disableButtons();
    }
}

export function setFGButton(col: string): void {
    document.getElementById("forecolor")!.style.backgroundColor = col;
}

export function toggleEditorButton(buttonid: string): void {
    const button = $(buttonid)[0];
    button.classList.toggle("highlighted");
}
