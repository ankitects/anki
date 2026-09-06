// Small, bounded reader for the package/config messages in proto/anki/.
// Unknown fields are skipped so added optional fields remain compatible.
type Value = bigint | Uint8Array;

export class Proto {
  private readonly fields = new Map<number, Value[]>();

  constructor(bytes: Uint8Array) {
    let offset = 0;
    const variable = () => {
      let value = 0n;
      for (let shift = 0n; shift < 70n && offset < bytes.length; shift += 7n) {
        const byte = bytes[offset++];
        value |= BigInt(byte & 127) << shift;
        if (!(byte & 128)) return value;
      }
      throw new Error("Invalid package metadata: truncated integer");
    };
    while (offset < bytes.length) {
      const tag = Number(variable());
      const field = Math.floor(tag / 8);
      if (!Number.isSafeInteger(tag) || !field) throw new Error("Invalid package metadata field");
      let value: Value;
      switch (tag % 8) {
        case 0: value = variable(); break;
        case 1:
        case 2:
        case 5: {
          const size = tag % 8 === 2 ? Number(variable()) : tag % 8 === 1 ? 8 : 4;
          if (!Number.isSafeInteger(size) || size < 0 || size > bytes.length - offset) {
            throw new Error("Invalid package metadata: truncated field");
          }
          value = bytes.subarray(offset, offset + size);
          offset += size;
          break;
        }
        default: throw new Error("Invalid package metadata wire type");
      }
      const values = this.fields.get(field) ?? [];
      values.push(value);
      this.fields.set(field, values);
    }
  }

  has(field: number) { return this.fields.has(field); }

  number(field: number, fallback = 0): number {
    const value = this.fields.get(field)?.at(-1);
    if (value === undefined) return fallback;
    if (typeof value !== "bigint") throw new Error("Invalid numeric metadata field");
    const number = Number(BigInt.asIntN(64, value));
    if (!Number.isSafeInteger(number)) throw new Error("Package ID exceeds the supported integer range");
    return number;
  }

  bytes(field: number): Uint8Array {
    const value = this.fields.get(field)?.at(-1);
    if (value === undefined) return new Uint8Array();
    if (!(value instanceof Uint8Array)) throw new Error("Invalid binary metadata field");
    return value;
  }

  text(field: number): string { return new TextDecoder("utf-8", { fatal: true }).decode(this.bytes(field)); }

  messages(field: number): Proto[] {
    return (this.fields.get(field) ?? []).map((value) => {
      if (!(value instanceof Uint8Array)) throw new Error("Invalid nested metadata field");
      return new Proto(value);
    });
  }

  numbers(field: number): number[] {
    return (this.fields.get(field) ?? []).flatMap((value) => {
      if (typeof value === "bigint") return [Number(value)];
      const output: number[] = [];
      let number = 0;
      let shift = 0;
      for (const byte of value) {
        number += (byte & 127) * 2 ** shift;
        if (!Number.isSafeInteger(number) || shift > 28) throw new Error("Invalid packed metadata integer");
        if (byte & 128) shift += 7;
        else { output.push(number); number = 0; shift = 0; }
      }
      if (shift) throw new Error("Truncated packed metadata integer");
      return output;
    });
  }
}
