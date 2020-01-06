/** Throws if argument is null/undefined. */
export function expectNotNull<T>(val: T | null | undefined): T {
  if (val === null || val === undefined) {
    throw Error("Unexpected missing value.");
  }
  return val as T;
}

//* Throws if argument is not truthy. */
export function assert<T>(val: T): asserts val {
  if (!val) {
    throw Error("Assertion failed.");
  }
}
