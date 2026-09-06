import { strToU8, zipSync } from "fflate";
import type { Zippable } from "fflate";

export type BackupMediaFile = {
  name: string;
  bytes: Uint8Array;
};

const MAX_MEDIA_FILES = 50_000;

function validMediaName(name: string) {
  return Boolean(name) && name !== "." && name !== ".." && !/[\\/\u0000-\u001f\u007f]/.test(name);
}

/** Build Anki's schema-11 collection package format. */
export function buildCollectionPackage(collection: Uint8Array, media: BackupMediaFile[]) {
  if (!collection.length || new TextDecoder().decode(collection.subarray(0, 16)) !== "SQLite format 3\0") {
    throw new Error("The local collection could not be serialized as an Anki database");
  }
  if (media.length > MAX_MEDIA_FILES) throw new Error("The collection contains too many media files to back up in the browser");

  const manifest: Record<string, string> = {};
  const archive: Zippable = {
    // A meta-less collection.anki2 package is Anki's legacy v1 format. The
    // PWA stores schema 11 already, so no database migration is needed.
    "collection.anki2": [collection, { level: 6 }]
  };
  const names = new Set<string>();
  media.forEach((file, index) => {
    const name = file.name.normalize("NFC");
    if (!validMediaName(name)) throw new Error("The collection contains an invalid media filename");
    if (names.has(name)) throw new Error(`The collection contains duplicate media: ${name}`);
    names.add(name);
    manifest[String(index)] = name;
    // Media is usually already compressed and Anki stores legacy media
    // entries without ZIP compression.
    archive[String(index)] = [file.bytes, { level: 0 }];
  });
  archive.media = [strToU8(JSON.stringify(manifest)), { level: 0 }];
  return zipSync(archive, { level: 6 });
}
