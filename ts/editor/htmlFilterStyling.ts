interface AllowPropertiesBlockValues {
    [property: string]: string[];
}

type BlockProperties = string[];

type StylingPredicate = (property: string, value: string) => boolean;

const stylingNightMode: AllowPropertiesBlockValues = {
    "font-weight": [],
    "font-style": [],
    "text-decoration-line": [],
};

const stylingLightMode: AllowPropertiesBlockValues = {
    color: [],
    "background-color": ["transparent"],
    ...stylingNightMode,
};

const stylingInternal: BlockProperties = [
    "background-color",
    "font-size",
    "font-family",
];

const allowPropertiesBlockValues = (
    allowBlock: AllowPropertiesBlockValues
): StylingPredicate => (property: string, value: string): boolean =>
    allowBlock.hasOwnProperty(property) && !allowBlock[property].includes(value);

const blockProperties = (block: BlockProperties): StylingPredicate => (
    property: string
): boolean => !block.includes(property);

const filterStyling = (predicate: (property: string, value: string) => boolean) => (
    element: HTMLElement
): void => {
    for (const property of [...element.style]) {
        const value = element.style.getPropertyValue(name);

        if (!predicate(property, value)) {
            element.style.removeProperty(property);
        }
    }
};

export const filterStylingNightMode = filterStyling(
    allowPropertiesBlockValues(stylingNightMode)
);
export const filterStylingLightMode = filterStyling(
    allowPropertiesBlockValues(stylingLightMode)
);
export const filterStylingInternal = filterStyling(blockProperties(stylingInternal));
