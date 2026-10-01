#!/usr/bin/env python3
"""Demo backend for the customer consent journey.

Consent Core is the bank system of record, but it needs Oracle and is not
running in this environment. This server is the demo stand-in for the
customer-facing flow: it registers a consent, prepares it, and records
approve or reject. The browser never invents the consent status.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import threading
import uuid
from datetime import datetime, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "data" / "consent-demo.db"
CORE_HEALTH = "http://127.0.0.1:8081/actuator/health"
PORT = 4173

ACCOUNTS = [
    {"id": "A001", "name": "Tài khoản thanh toán", "number": "0123 456 789", "balance": "24.580.000 ₫"},
    {"id": "A002", "name": "Tài khoản tiết kiệm", "number": "9876 543 210", "balance": "120.000.000 ₫"},
    {"id": "A003", "name": "Tài khoản lương", "number": "5566 7788 990", "balance": "18.200.000 ₫"},
]
ACCOUNT_BY_ID = {item["id"]: item for item in ACCOUNTS}
PERMISSIONS = [
    "Xem số dư các tài khoản đã chọn",
    "Xem lịch sử giao dịch trong 90 ngày",
]
LOCK = threading.Lock()


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def digest(payload: dict) -> str:
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(raw).hexdigest()


def connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with LOCK, connect() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS journey (
                id TEXT PRIMARY KEY,
                consent_id TEXT NOT NULL,
                scenario TEXT NOT NULL,
                status TEXT NOT NULL,
                subject_ref TEXT,
                revision_no INTEGER,
                content_hash TEXT,
                selection_hash TEXT,
                selected_json TEXT,
                presentation_json TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS journey_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                journey_id TEXT NOT NULL,
                action TEXT NOT NULL,
                status_after TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """
        )


def core_reachable() -> bool:
    try:
        with urlopen(CORE_HEALTH, timeout=0.4) as response:
            return 200 <= response.status < 300
    except (URLError, TimeoutError, OSError):
        return False


def row_public(row: sqlite3.Row) -> dict:
    presentation = json.loads(row["presentation_json"]) if row["presentation_json"] else None
    selected = json.loads(row["selected_json"]) if row["selected_json"] else []
    return {
        "journeyId": row["id"],
        "consentId": row["consent_id"],
        "scenario": row["scenario"],
        "status": row["status"],
        "subjectRef": row["subject_ref"],
        "revisionNo": row["revision_no"],
        "contentHash": row["content_hash"],
        "selectionContextHash": row["selection_hash"],
        "selectedAccountIds": selected,
        "presentation": presentation,
        "updatedAt": row["updated_at"],
    }


def add_event(conn: sqlite3.Connection, journey_id: str, action: str, status_after: str) -> None:
    conn.execute(
        "INSERT INTO journey_event (journey_id, action, status_after, created_at) VALUES (?, ?, ?, ?)",
        (journey_id, action, status_after, now()),
    )


def load(conn: sqlite3.Connection, journey_id: str) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM journey WHERE id = ?", (journey_id,)).fetchone()
    if row is None:
        raise ApiError(404, "Không tìm thấy phiên đồng ý.")
    return row


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def create_journey(scenario: str) -> dict:
    if scenario not in {"standard", "denied", "already"}:
        raise ApiError(400, "Kịch bản không hợp lệ.")
    journey_id = str(uuid.uuid4())
    consent_id = str(uuid.uuid4())
    timestamp = now()
    status = "AUTHORIZED" if scenario == "already" else "REGISTERED"
    revision_no = 1 if scenario == "already" else None
    presentation = None
    content_hash = None
    if scenario == "already":
        presentation = {
            "title": "Chia sẻ thông tin tài khoản",
            "summary": "Đồng ý đã được ghi nhận trước đó.",
            "permissions": PERMISSIONS,
            "accounts": [account_view(ACCOUNTS[0])],
            "clientName": "FinConnect",
            "validUntil": "01/12/2026",
            "frequency": "Tối đa 4 lần mỗi ngày",
        }
        content_hash = digest({"revision": 1, "presentation": presentation})
    with LOCK, connect() as conn:
        conn.execute(
            """
            INSERT INTO journey (
                id, consent_id, scenario, status, subject_ref, revision_no, content_hash,
                selection_hash, selected_json, presentation_json, created_at, updated_at
            ) VALUES (?, ?, ?, ?, NULL, ?, ?, NULL, NULL, ?, ?, ?)
            """,
            (
                journey_id,
                consent_id,
                scenario,
                status,
                revision_no,
                content_hash,
                json.dumps(presentation, ensure_ascii=False) if presentation else None,
                timestamp,
                timestamp,
            ),
        )
        add_event(conn, journey_id, "REGISTER", status)
        return row_public(load(conn, journey_id))


def authenticate(journey_id: str, username: str, password: str) -> dict:
    username = username.strip()
    if not username or len(password) < 4:
        raise ApiError(400, "Nhập tên đăng nhập và mật khẩu từ 4 ký tự.")
    with LOCK, connect() as conn:
        row = load(conn, journey_id)
        scenario = row["scenario"]
        if scenario == "denied":
            conn.execute(
                "UPDATE journey SET subject_ref = ?, updated_at = ? WHERE id = ?",
                (username, now(), journey_id),
            )
            add_event(conn, journey_id, "PREPARE_DENIED", "REGISTERED")
            body = row_public(load(conn, journey_id))
            body["preparationStatus"] = "DENIED"
            return body
        if scenario == "already":
            if row["status"] != "AUTHORIZED":
                raise ApiError(409, "Consent đã đồng ý trước đó không còn hiệu lực.")
            conn.execute(
                "UPDATE journey SET subject_ref = ?, updated_at = ? WHERE id = ?",
                (username, now(), journey_id),
            )
            add_event(conn, journey_id, "AUTHENTICATE_EXISTING", "AUTHORIZED")
            body = row_public(load(conn, journey_id))
            body["preparationStatus"] = "ALREADY_AUTHORIZED"
            return body
        if row["status"] != "REGISTERED":
            raise ApiError(409, "Consent không còn ở bước chuẩn bị.")
        selection_hash = digest({"subjectRef": username, "accounts": [item["id"] for item in ACCOUNTS]})
        conn.execute(
            "UPDATE journey SET subject_ref = ?, selection_hash = ?, updated_at = ? WHERE id = ?",
            (username, selection_hash, now(), journey_id),
        )
        add_event(conn, journey_id, "PREPARE_SELECTION_REQUIRED", "REGISTERED")
        body = row_public(load(conn, journey_id))
        body["preparationStatus"] = "SELECTION_REQUIRED"
        body["accounts"] = ACCOUNTS
        return body


def select_accounts(journey_id: str, account_ids: list, selection_hash: str) -> dict:
    if not isinstance(account_ids, list) or not account_ids:
        raise ApiError(400, "Chọn ít nhất một tài khoản.")
    unknown = [item for item in account_ids if item not in ACCOUNT_BY_ID]
    if unknown:
        raise ApiError(400, "Danh sách tài khoản không hợp lệ.")
    with LOCK, connect() as conn:
        row = load(conn, journey_id)
        if row["status"] != "REGISTERED" or row["scenario"] != "standard":
            raise ApiError(409, "Consent không đang chờ chọn tài khoản.")
        if selection_hash != row["selection_hash"]:
            raise ApiError(409, "Danh sách tài khoản đã cũ. Hãy tải lại.")
        chosen = [account_view(ACCOUNT_BY_ID[item]) for item in account_ids]
        presentation = {
            "title": "Chia sẻ thông tin tài khoản",
            "summary": "FinConnect xin phép truy cập đến ngày 01/12/2026, tối đa 4 lần mỗi ngày.",
            "permissions": PERMISSIONS,
            "accounts": chosen,
            "clientName": "FinConnect",
            "validUntil": "01/12/2026",
            "frequency": "Tối đa 4 lần mỗi ngày",
        }
        revision_no = 1
        content_hash = digest({"revision": revision_no, "subjectRef": row["subject_ref"], "presentation": presentation})
        conn.execute(
            """
            UPDATE journey
               SET status = 'AWAITING_AUTHORIZATION',
                   revision_no = ?,
                   content_hash = ?,
                   selected_json = ?,
                   presentation_json = ?,
                   updated_at = ?
             WHERE id = ?
            """,
            (revision_no, content_hash, json.dumps(account_ids), json.dumps(presentation, ensure_ascii=False), now(), journey_id),
        )
        add_event(conn, journey_id, "PREPARE", "AWAITING_AUTHORIZATION")
        body = row_public(load(conn, journey_id))
        body["preparationStatus"] = "PREPARED"
        return body


def decide(journey_id: str, decision: str, revision_no: int, content_hash: str) -> dict:
    if decision not in {"APPROVE", "REJECT"}:
        raise ApiError(400, "Quyết định không hợp lệ.")
    next_status = "AUTHORIZED" if decision == "APPROVE" else "REJECTED"
    with LOCK, connect() as conn:
        row = load(conn, journey_id)
        if row["status"] in {"AUTHORIZED", "REJECTED"} and row["revision_no"] == revision_no and row["content_hash"] == content_hash:
            body = row_public(row)
            body["decision"] = "approved" if row["status"] == "AUTHORIZED" else "rejected"
            return body
        if row["status"] != "AWAITING_AUTHORIZATION":
            raise ApiError(409, "Consent chưa sẵn sàng để quyết định.")
        if revision_no != row["revision_no"] or content_hash != row["content_hash"]:
            raise ApiError(409, "Nội dung trên màn hình không còn khớp bản đã chốt.")
        conn.execute(
            "UPDATE journey SET status = ?, updated_at = ? WHERE id = ?",
            (next_status, now(), journey_id),
        )
        add_event(conn, journey_id, decision, next_status)
        body = row_public(load(conn, journey_id))
        body["decision"] = "approved" if next_status == "AUTHORIZED" else "rejected"
        return body


def account_view(account: dict) -> dict:
    return {"id": account["id"], "name": account["name"], "number": account["number"]}


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_GET(self) -> None:
        path = self.path.split("?", 1)[0]
        if path == "/api/health":
            self.respond(200, {"demoApi": "up", "consentCore": "up" if core_reachable() else "down"})
            return
        if path.startswith("/api/journeys/"):
            journey_id = path.removeprefix("/api/journeys/").strip("/")
            try:
                with LOCK, connect() as conn:
                    body = row_public(load(conn, journey_id))
                self.respond(200, body)
            except ApiError as error:
                self.respond(error.status, {"error": error.message})
            return
        if path.startswith("/api/"):
            self.respond(404, {"error": "Không có API này."})
            return
        super().do_GET()

    def do_POST(self) -> None:
        path = self.path.split("?", 1)[0]
        try:
            payload = self.read_json()
            if path == "/api/journeys":
                body = create_journey(str(payload.get("scenario") or "standard"))
            elif path.endswith("/authenticate") and path.startswith("/api/journeys/"):
                journey_id = path.split("/")[3]
                body = authenticate(journey_id, str(payload.get("username") or ""), str(payload.get("password") or ""))
            elif path.endswith("/selection") and path.startswith("/api/journeys/"):
                journey_id = path.split("/")[3]
                body = select_accounts(journey_id, payload.get("accountIds") or [], str(payload.get("selectionContextHash") or ""))
            elif path.endswith("/decision") and path.startswith("/api/journeys/"):
                journey_id = path.split("/")[3]
                body = decide(
                    journey_id,
                    str(payload.get("decision") or ""),
                    int(payload.get("revisionNo") or 0),
                    str(payload.get("contentHash") or ""),
                )
            else:
                raise ApiError(404, "Không có API này.")
            self.respond(200, body)
        except ApiError as error:
            self.respond(error.status, {"error": error.message})
        except (TypeError, ValueError):
            self.respond(400, {"error": "Dữ liệu gửi lên không hợp lệ."})

    def read_json(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if length == 0:
            return {}
        raw = self.rfile.read(length)
        data = json.loads(raw.decode())
        if not isinstance(data, dict):
            raise ApiError(400, "Dữ liệu gửi lên không hợp lệ.")
        return data

    def respond(self, status: int, body: dict) -> None:
        raw = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, format: str, *args) -> None:
        print("%s - %s" % (self.address_string(), format % args))


def main() -> None:
    init_db()
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Demo consent API http://127.0.0.1:{PORT}/")
    server.serve_forever()


if __name__ == "__main__":
    main()
