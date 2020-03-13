from bottle import run, route, request, response
from anki.pybackend import PythonBackend
from anki import Collection
import sys, os

path = os.path.expanduser("~/test.anki2")
if not os.path.exists(path):
    print(f"to use, copy your collection to {path}")
    sys.exit(1)

col = Collection(path)
backend = PythonBackend(col)

@route('/request', method="POST")
def proto_request():
    response.content_type = 'application/protobuf'
    return backend.run_command_bytes(request.body.read())

run(host='localhost', port=5006)
