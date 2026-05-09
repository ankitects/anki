// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Names of anki packages
 *
 * @privateRemarks
 * Originally this was more strictly typed as a record:
 * ```ts
 * type AnkiPackages = {
 *     "anki/NoteEditor": NoteEditorPackage,
 * }
 * ```
 * This would be very useful for `require`: the result could be strictly typed.
 * However cross-module type imports currently don't work.
 */
type AnkiPackages =
    | "anki/NoteEditor"
    | "anki/EditorField"
    | "anki/PlainTextInput"
    | "anki/RichTextInput"
    | "anki/TemplateButtons"
    | "anki/packages"
    | "anki/bridgecommand"
    | "anki/shortcuts"
    | "anki/theme"
    | "anki/location"
    | "anki/surround"
    | "anki/ui"
    | "anki/reviewer";
type PackageDeprecation<T extends Record<string, unknown>> = {
    [key in keyof T]?: string;
};

/** This can be extended to allow require() calls at runtime, for packages
that are not included at bundling time. */
const runtimePackages: Partial<Record<AnkiPackages, Record<string, unknown>>> = {};
const prohibit = () => false;

/**
 * Packages registered with this function escape the typing provided by `AnkiPackages`
 */
export function registerPackageRaw(
    name: string,
    entries: Record<string, unknown>,
): void {
    runtimePackages[name] = entries;
}

export function registerPackage<
    T extends AnkiPackages,
    U extends Record<string, unknown>,
>(name: T, entries: U, deprecation?: PackageDeprecation<U>): void {
    const pack = deprecation
        ? new Proxy(entries, {
            set: prohibit,
            defineProperty: prohibit,
            deleteProperty: prohibit,
            get: (target, name: string) => {
                if (name in deprecation) {
                    console.log(`anki: ${name} is deprecated: ${deprecation[name]}`);
                }

                return target[name];
            },
        })
        : entries;

    registerPackageRaw(name, pack);
}

function require<T extends AnkiPackages>(name: T): Record<string, unknown> | undefined {
    if (!(name in runtimePackages)) {
        throw new Error(`Cannot require "${name}" at runtime.`);
    } else {
        return runtimePackages[name];
    }
}

function listPackages(): string[] {
    return Object.keys(runtimePackages);
}

function hasPackages(...names: string[]): boolean {
    for (const name of names) {
        if (!(name in runtimePackages)) {
            return false;
        }
    }

    return true;
}

// Export require() as a global.
Object.assign(globalThis, { require });

registerPackage("anki/packages", {
    // We also register require here, so add-ons can have a type-save variant of require (TODO, see AnkiPackages above)
    require,
    listPackages,
    hasPackages,
});
