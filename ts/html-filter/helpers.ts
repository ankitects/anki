export function isHTMLElement(elem: Element): elem is HTMLElement {
    return elem instanceof HTMLElement;
}

export function isNightMode(): boolean {
    return document.body.classList.contains("nightMode");
}
