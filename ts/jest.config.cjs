module.exports = {
    testMatch: ["**/*.test.ts"],
    transform: {
        "\\.(ts|js)$": [
            "<rootDir>/esbuild_jest.mjs",
            {},
        ],
    },
    moduleNameMapper: {
        "^@tslib(.*)$": [
            "<rootDir>/lib$1",
        ],
        "^@generated(.*)$": [
            "<rootDir>/../out/ts/generated$1",
        ],
    },
    transformIgnorePatterns: ["/node_modules/(?!(lodash-es|svelte)/)"],
};
