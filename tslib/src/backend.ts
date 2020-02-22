import { backend_proto as pb } from "../dist/backend_pb";
import { expectNotNull } from "./tsutils";
export { pb };

const BACKEND_URL = "http://localhost:3000/request";

function responseError(err: pb.BackendError): Error {
  switch (err.value) {
    case "invalidInput":
      return Error(`invalid input: ${err.invalidInput?.info}`);
    case "templateParse":
      return Error(`template parse failed: ${err.templateParse?.info}`);
    default:
      return Error("unexpected error response value");
  }
}

/** Encode and send a request, decoding and returning the response.
 * Throws on error.
 */
async function webRequest(input: pb.IBackendInput): Promise<pb.BackendOutput> {
  // encode request
  const err = pb.BackendInput.verify(input);
  if (err) {
    throw Error(err);
  }
  const buf = pb.BackendInput.encode(input).finish();

  // send to backend
  const resp = await fetch(BACKEND_URL, {
    method: "POST",
    body: buf,
    headers: {
      "Content-Type": "application/protobuf",
      "Accept": "application/protobuf",
    }
  });
  if (!resp.ok) {
    throw Error(`unexpected reply: ${resp.statusText}`);
  }

  // get returned bytes
  const respBlob = await resp.blob();
  const respBuf = await new Response(respBlob).arrayBuffer();

  // decode response, throwing on error/missing
  const result = pb.BackendOutput.decode(new Uint8Array(respBuf));
  if (result.value === undefined) {
    throw Error("Unexpected vaule in backend output.");
  } else if (result.value === "error") {
    throw responseError(result?.error as pb.BackendError);
  } else {
    return result;
  }
}

export async function deckTree(): Promise<pb.IDeckTreeNode[]> {
  const resp = await webRequest({
    deckTree: new pb.Empty()
  });
  return expectNotNull(resp?.deckTree?.top?.children);
}

export async function findCards(search: string): Promise<number[]> {
  const resp = await webRequest({
    findCards: new pb.FindCardsIn({ search })
  });
  return expectNotNull(resp?.findCards?.cardIds) as number[];
}

// just sort field for now
export async function browserRows(cardIds: number[]): Promise<string[]> {
  const resp = await webRequest({
    browserRows: new pb.BrowserRowsIn({ cardIds })
  });
  return expectNotNull(resp?.browserRows?.sortFields);
}
