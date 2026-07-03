// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export const changePreviewHTMLView = (): void => {
    const activeElement = document.activeElement!;
    if (!activeElement || !activeElement.id.includes("--")) {
        return;
    }

    const noteId = activeElement.id.split("--")[0];
    const divId = `${noteId}--div`;
    const textAreaId = `${noteId}--textarea`;
    const divElement = document.getElementById(divId)!;
    const textAreaElement = document.getElementById(textAreaId)! as HTMLTextAreaElement;

    if (divElement.style.display == "none") {
        divElement.style.display = "block";
        textAreaElement.style.display = "none";
        divElement.focus();
    } else {
        divElement.style.display = "none";
        textAreaElement.style.display = "block";
        textAreaElement.focus();
    }
};
