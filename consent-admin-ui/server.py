#!/usr/bin/env python3
"""Consent admin UI.

Login is local to this server. After login, consent list requests are
proxied to Consent Core with the trusted header context of the signed-in user.
"""

from __future__ import annotations

import json
import secrets
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
CORE = "http://127.0.0.1:8081"
PORT = 4180

USERS = {
    "admin": {
        "password": "admin123",
        "displayName": "Quản trị viên",
        "tenantId": "open-banking",
        "actorId": "admin",
        "actorType": "USER",
    }
}
SESSIONS: dict[str, dict] = {}


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_GET(self):
        if self.path.startswith("/api/"):
            self.proxy()
            return
        if self.path in ("/", ""):
            self.path = "/index.html"
        super().do_GET()

    def do_POST(self):
        if self.path == "/auth/login":
            self.login()
            return
        self.send_json(404, {"error": "Không tìm thấy API."})

    def login(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length) or b"{}")
        except (json.JSONDecodeError, ValueError):
            self.send_json(400, {"error": "Dữ liệu đăng nhập không hợp lệ."})
            return
        username = str(body.get("username", "")).strip()
        password = str(body.get("password", ""))
        user = USERS.get(username)
        if user is None or user["password"] != password:
            self.send_json(401, {"error": "Sai tên đăng nhập hoặc mật khẩu."})
            return
        token = secrets.token_urlsafe(24)
        SESSIONS[token] = user
        self.send_json(200, {
            "token": token,
            "displayName": user["displayName"],
            "username": username,
            "tenantId": user["tenantId"],
        })

    def proxy(self):
        token = self.headers.get("Authorization", "").removeprefix("Bearer ").strip()
        user = SESSIONS.get(token)
        if user is None:
            self.send_json(401, {"error": "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."})
            return
        request = Request(
            CORE + self.path,
            headers={
                "Accept": "application/json",
                "X-Tenant-Id": user["tenantId"],
                "X-Actor-Id": user["actorId"],
                "X-Actor-Type": user["actorType"],
                "X-Source-System": "CONSENT_ADMIN_UI",
            },
            method="GET",
        )
        try:
            with urlopen(request, timeout=15) as response:
                payload = response.read()
                status = response.status
                content_type = response.headers.get("Content-Type", "application/json")
        except HTTPError as error:
            payload = error.read()
            status = error.code
            content_type = error.headers.get("Content-Type", "application/json")
        except (URLError, TimeoutError, OSError):
            self.send_json(502, {"error": "Consent Core chưa sẵn sàng tại cổng 8081."})
            return
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def send_json(self, status: int, payload: dict) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Consent admin UI http://127.0.0.1:{PORT}", flush=True)
    server.serve_forever()
