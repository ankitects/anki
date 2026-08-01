/** @type {import('@sveltejs/kit').HandleClientError} */
export async function handleError({ error, event, status, message }) {
    /** @type {any} */
    const anyError = error;
    return {
        message: anyError.message,
    };
}
