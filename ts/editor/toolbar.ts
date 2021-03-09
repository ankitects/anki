/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

const highlightButtons = ["bold", "italic", "underline", "superscript", "subscript"];

export function updateButtonState(): void {
    for (const name of highlightButtons) {
        const elem = document.querySelector(`#${name}`) as HTMLElement;
        elem.classList.toggle("highlighted", document.queryCommandState(name));
    }

    // fixme: forecolor
    //    'col': document.queryCommandValue("forecolor")
}

function clearButtonHighlight(): void {
    for (const name of highlightButtons) {
        const elem = document.querySelector(`#${name}`) as HTMLElement;
        elem.classList.remove("highlighted");
    }
}

export function preventButtonFocus(): void {
    for (const element of document.querySelectorAll("button.linkb")) {
        element.addEventListener("mousedown", (evt: Event) => {
            evt.preventDefault();
        });
    }
}

export function enableButtons(): void {
    const buttons = document.querySelectorAll(
        "button.linkb"
    ) as NodeListOf<HTMLButtonElement>;
    buttons.forEach((elem: HTMLButtonElement): void => {
        elem.disabled = false;
    });
    updateButtonState();
}

export function disableButtons(): void {
    const buttons = document.querySelectorAll(
        "button.linkb:not(.perm)"
    ) as NodeListOf<HTMLButtonElement>;
    buttons.forEach((elem: HTMLButtonElement): void => {
        elem.disabled = true;
    });
    clearButtonHighlight();
}

export function setFGButton(col: string): void {
    document.getElementById("forecolor")!.style.backgroundColor = col;
}

export function toggleEditorButton(buttonOrId: string | HTMLElement): void {
    const button =
        typeof buttonOrId === "string"
            ? (document.getElementById(buttonOrId) as HTMLElement)
            : buttonOrId;
    button.classList.toggle("highlighted");
}
