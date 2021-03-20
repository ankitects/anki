window.require = (name) => {
    if (name === "protobufjs/light") return window.protobuf;
    else throw new Error(`Cannot require ${name}`);
};
