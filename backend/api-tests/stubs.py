#!/usr/bin/env python3
"""Stubs locais para os testes de API do VanRoute (sem dependencias externas).

- SMTP sink em 127.0.0.1:2525: aceita qualquer AUTH e grava os e-mails em api-tests/out/mail.log
- Geocoder fake em 127.0.0.1:8089/pelias/v1/search: devolve coordenadas fixas (formato Pelias)
  e uma lista vazia quando o texto contem "NAOEXISTE" (simula endereco nao encontrado).
"""
import json
import os
import socketserver
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
os.makedirs(OUT, exist_ok=True)
MAIL_LOG = os.path.join(OUT, "mail.log")


class SmtpHandler(socketserver.StreamRequestHandler):
    def send(self, line):
        self.wfile.write((line + "\r\n").encode())

    def handle(self):
        self.send("220 stub ESMTP")
        while True:
            raw = self.rfile.readline()
            if not raw:
                return
            cmd = raw.decode(errors="replace").strip()
            up = cmd.upper()
            if up.startswith("EHLO") or up.startswith("HELO"):
                self.wfile.write(b"250-stub\r\n250-AUTH PLAIN LOGIN\r\n250 OK\r\n")
            elif up.startswith("AUTH LOGIN"):
                self.send("334 VXNlcm5hbWU6")
                self.rfile.readline()
                self.send("334 UGFzc3dvcmQ6")
                self.rfile.readline()
                self.send("235 OK")
            elif up.startswith("AUTH"):
                if len(cmd.split()) == 2:
                    self.send("334 ")
                    self.rfile.readline()
                self.send("235 OK")
            elif up == "DATA":
                self.send("354 end with .")
                lines = []
                while True:
                    line = self.rfile.readline().decode(errors="replace")
                    if line.rstrip("\r\n") == "." or not line:
                        break
                    lines.append(line)
                with open(MAIL_LOG, "a") as f:
                    f.write("".join(lines) + "\n=====\n")
                self.send("250 queued")
            elif up == "QUIT":
                self.send("221 bye")
                return
            else:
                self.send("250 OK")


class GeoHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        text = parse_qs(urlparse(self.path).query).get("text", [""])[0]
        features = [] if "NAOEXISTE" in text.upper() else [
            {"geometry": {"type": "Point", "coordinates": [-46.6333, -23.5505]}}]
        body = json.dumps({"features": features}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    smtp = socketserver.ThreadingTCPServer(("127.0.0.1", 2525), SmtpHandler)
    threading.Thread(target=smtp.serve_forever, daemon=True).start()
    print("stubs: smtp 127.0.0.1:2525, geocoder 127.0.0.1:8089", flush=True)
    HTTPServer(("127.0.0.1", 8089), GeoHandler).serve_forever()
