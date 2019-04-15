import http.server
import socketserver
from http import HTTPStatus
import json
import os

PORT = 8000

class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(HTTPStatus.OK)
        self.end_headers()

        self.wfile.write(bytes(json.dumps(dict(os.environ)), encoding='utf8'))


httpd = socketserver.TCPServer(("", PORT), Handler)
print("serving at port", PORT)

httpd.serve_forever()

