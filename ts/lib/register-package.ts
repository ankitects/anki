// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { runtimeLibraries } from "./runtime-require";

const prohibit = () => false;

export function registerPackage(
    name: string,
    entries: Record<string, unknown>,
    deprecation?: Record<string, string>
): void {
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

    runtimeLibraries[name] = pack;
}

function listPackages(): string[] {
    return Object.keys(runtimeLibraries);
}

function hasPackages(...names: string[]): boolean {
    const libraries = listPackages();
    return names.reduce(
        (accu: boolean, name: string) => accu && libraries.includes(name),
        true
    );
}

function immediatelyDeprecated() {
    return false;
}

registerPackage(
    "anki/packages",
    {
        listPackages,
        hasPackages,
        immediatelyDeprecated,
    },
    {
        [immediatelyDeprecated.name]: "Do not use this function",
    }
);
