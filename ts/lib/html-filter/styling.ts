// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/** Keep property if true. */
type StylingPredicate = (element: HTMLElement, property: string, value: string) => boolean;

const keep = (_element: HTMLElement, _key: string, _value: string) => true;
const discard = (_element: HTMLElement, _key: string, _value: string) => false;
const discardIfImage = (element: HTMLElement, _key: string, _value: string) => element.nodeName !== "IMG";

/** Return a function that filters out certain styles.
   - If the style is listed in `exceptions`, the provided predicate is used.
   - If the style is not listed, the default predicate is used instead. */
function filterStyling(
    defaultPredicate: StylingPredicate,
    exceptions: Record<string, StylingPredicate>,
): (element: HTMLElement) => void {
    return (element: HTMLElement): void => {
        // jsdom does not support @@iterator, so manually iterate
        const toRemove: string[] = [];
        for (let i = 0; i < element.style.length; i++) {
            const key = element.style.item(i);
            const value = element.style.getPropertyValue(key);
            const predicate = exceptions[key] ?? defaultPredicate;
            if (!predicate(element, key, value)) {
                toRemove.push(key);
            }
        }
        for (const key of toRemove) {
            element.style.removeProperty(key);
        }
    };
}

const nightModeExceptions = {
    "font-weight": keep,
    "font-style": keep,
    "text-decoration-line": keep,
};

export const filterStylingNightMode = filterStyling(discard, nightModeExceptions);
export const filterStylingLightMode = filterStyling(discard, {
    color: keep,
    "background-color": (_element: HTMLElement, _key: string, value: string) => value != "transparent",
    ...nightModeExceptions,
});
export const filterStylingInternal = filterStyling(keep, {
    "font-size": discard,
    "font-family": discard,
    width: discard,
    height: discard,
    "max-width": discard,
    "max-height": discard,
    "vertical-align": discardIfImage,
});
