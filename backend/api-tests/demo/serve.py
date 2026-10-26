"""Serve a pagina demo da trip em http://localhost:8090 e repassa /api/* pro backend.

O backend nao tem CORS configurado, entao a pagina nao pode chamar :9090 direto do navegador;
por aqui tudo fica na mesma origem. O WebSocket vai direto no backend (ele aceita qualquer origem).

    python3 -I backend/api-tests/demo/serve.py            # backend em http://localhost:9090
    BACKEND=http://host:porta python3 -I .../serve.py
"""
import os
import urllib.error
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

BACKEND = os.environ.get("BACKEND", "http://localhost:9090")
PORT = int(os.environ.get("PORT", "8090"))
ROOT = Path(__file__).resolve().parent


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def _proxy(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        headers = {k: v for k, v in self.headers.items() if k.lower() in ("authorization", "content-type")}
        req = urllib.request.Request(BACKEND + self.path, data=body, headers=headers, method=self.command)
        try:
            with urllib.request.urlopen(req, timeout=30) as res:
                status, data, ctype = res.status, res.read(), res.headers.get("Content-Type")
        except urllib.error.HTTPError as e:
            status, data, ctype = e.code, e.read(), e.headers.get("Content-Type")
        except urllib.error.URLError as e:
            status, data, ctype = 502, f"backend fora do ar: {e.reason}".encode(), "text/plain"
        self.send_response(status)
        if ctype:
            self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/api/"):
            return self._proxy()
        if self.path == "/demo.json":
            # logins do cenario criado pelo seed.sh (fica em api-tests/out, fora do git)
            data = (ROOT.parent / "out" / "demo.json").read_bytes() if (ROOT.parent / "out" / "demo.json").exists() else b"{}"
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        return super().do_GET()

    def do_POST(self):
        self._proxy()

    def do_PUT(self):
        self._proxy()

    def do_DELETE(self):
        self._proxy()


if __name__ == "__main__":
    print(f"pagina demo em http://localhost:{PORT}  (backend {BACKEND})")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
