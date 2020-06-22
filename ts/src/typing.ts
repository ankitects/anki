export function assertUnreachable(x: never): never {
    throw new Error(`unreachable: ${x}`);
}
