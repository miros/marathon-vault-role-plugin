import http.server
import socketserver
from http import HTTPStatus
import json
import os
import sys

port = int(sys.argv[1])

class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(HTTPStatus.OK)
        self.end_headers()

        self.wfile.write(bytes(json.dumps(dict(os.environ)), encoding='utf8'))


httpd = socketserver.TCPServer(("", port), Handler)
print("serving at port", port)

httpd.serve_forever()

