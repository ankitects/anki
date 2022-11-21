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
            "<rootDir>/../out/ts/lib$1",
            "<rootDir>/lib$1",
        ],
    },
    transformIgnorePatterns: ["/node_modules/(?!(lodash-es)/)"],
};
