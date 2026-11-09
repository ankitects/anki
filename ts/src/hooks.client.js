/** @type {import('@sveltejs/kit/hooks').HandleClientError} */
export async function handleError({ error }) {
    /** @type {any} */
    const anyError = error;
    return {
        message: anyError.message,
    };
}
