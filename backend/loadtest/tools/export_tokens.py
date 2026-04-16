#!/usr/bin/env python3
"""
Prepare the seckill load-test context in one run:
1. create or reuse a seckill voucher
2. bulk upsert load-test users into MySQL
3. login those users to fetch access tokens
4. generate k6-ready files and commands
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
import sys
import textwrap
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable, List, Sequence


@dataclass
class VoucherContext:
    voucher_id: str
    title: str
    stock: int
    begin_time: str
    end_time: str
    created: bool


def build_emails(prefix: str, domain: str, count: int, start_index: int) -> List[str]:
    return [f"{prefix}{i}@{domain}" for i in range(start_index, start_index + count)]


def md5_hex(raw: str) -> str:
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def mysql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "\\'")


def json_request(url: str, payload: dict, timeout: int) -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"HTTP {exc.code} from {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Cannot access {url}: {exc}") from exc


def run_command(
    command: Sequence[str],
    capture_output: bool = True,
    input_text: str | None = None,
) -> str:
    completed = subprocess.run(
        list(command),
        check=True,
        capture_output=capture_output,
        text=True,
        input=input_text,
    )
    if not capture_output:
        return ""
    return completed.stdout.strip()


def run_mysql_sql(
    container: str,
    db_name: str,
    mysql_user: str,
    mysql_password: str,
    sql: str,
    capture_output: bool = True,
) -> str:
    command = [
        "docker",
        "exec",
        "-i",
        container,
        "mysql",
        "-N",
        "-B",
        f"-u{mysql_user}",
        f"-p{mysql_password}",
        db_name,
    ]
    return run_command(command, capture_output=capture_output, input_text=sql)


def run_redis_command(
    container: str,
    redis_password: str,
    redis_db: int,
    *args: str,
    capture_output: bool = False,
) -> str:
    command = [
        "docker",
        "exec",
        container,
        "redis-cli",
        "-a",
        redis_password,
        "-n",
        str(redis_db),
        *args,
    ]
    return run_command(command, capture_output=capture_output)


def chunked(items: List[str], size: int) -> Iterable[List[str]]:
    for index in range(0, len(items), size):
        yield items[index:index + size]


def ensure_users(
    container: str,
    db_name: str,
    mysql_user: str,
    mysql_password: str,
    emails: List[str],
    password: str,
    nickname_prefix: str,
    start_index: int,
    batch_size: int,
) -> None:
    password_md5 = md5_hex(password)
    for batch_start, email_batch in enumerate(chunked(emails, batch_size)):
        value_rows = []
        global_offset = batch_start * batch_size
        for offset, email in enumerate(email_batch):
            nick_name = f"{nickname_prefix}{start_index + global_offset + offset}"
            value_rows.append(
                "('{email}', '{password_md5}', '{nick_name}')".format(
                    email=mysql_escape(email),
                    password_md5=password_md5,
                    nick_name=mysql_escape(nick_name),
                )
            )

        sql = textwrap.dedent(
            f"""
            INSERT INTO tb_user (email, password, nick_name)
            VALUES {", ".join(value_rows)}
            ON DUPLICATE KEY UPDATE
              password = VALUES(password),
              nick_name = VALUES(nick_name);
            """
        ).strip()

        run_mysql_sql(
            container=container,
            db_name=db_name,
            mysql_user=mysql_user,
            mysql_password=mysql_password,
            sql=sql,
            capture_output=False,
        )


def create_seckill_voucher(args: argparse.Namespace, now: datetime) -> VoucherContext:
    begin_time = now - timedelta(minutes=args.begin_offset_minutes)
    end_time = now + timedelta(minutes=args.duration_minutes)
    title_suffix = now.strftime("%Y%m%d%H%M%S")
    title = args.voucher_title or f"Load Test Voucher {title_suffix}"
    sub_title = args.voucher_sub_title or "Auto-generated for k6 seckill load testing"
    rules = args.voucher_rules or "Load-test only. Auto-generated by export_tokens.py"

    sql = textwrap.dedent(
        f"""
        INSERT INTO tb_voucher (title, sub_title, rules, pay_value, actual_value, type, status)
        VALUES (
          '{mysql_escape(title)}',
          '{mysql_escape(sub_title)}',
          '{mysql_escape(rules)}',
          {args.pay_value},
          {args.actual_value},
          1,
          1
        );
        SET @voucher_id = LAST_INSERT_ID();
        INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time)
        VALUES (
          @voucher_id,
          {args.stock},
          '{begin_time.strftime("%Y-%m-%d %H:%M:%S")}',
          '{end_time.strftime("%Y-%m-%d %H:%M:%S")}'
        );
        SELECT @voucher_id;
        """
    ).strip()

    output = run_mysql_sql(
        container=args.mysql_container,
        db_name=args.db_name,
        mysql_user=args.mysql_user,
        mysql_password=args.mysql_password,
        sql=sql,
    )
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError("MySQL did not return the newly created voucher id")
    voucher_id = lines[-1]

    sync_seckill_stock(
        redis_container=args.redis_container,
        redis_password=args.redis_password,
        redis_db=args.redis_db,
        voucher_id=voucher_id,
        stock=args.stock,
    )

    return VoucherContext(
        voucher_id=voucher_id,
        title=title,
        stock=args.stock,
        begin_time=begin_time.strftime("%Y-%m-%d %H:%M:%S"),
        end_time=end_time.strftime("%Y-%m-%d %H:%M:%S"),
        created=True,
    )


def sync_seckill_stock(
    redis_container: str,
    redis_password: str,
    redis_db: int,
    voucher_id: str,
    stock: int,
) -> None:
    run_redis_command(redis_container, redis_password, redis_db, "SET", f"seckill:stock:{voucher_id}", str(stock))
    run_redis_command(redis_container, redis_password, redis_db, "DEL", f"seckill:order:{voucher_id}")


def build_voucher_context(args: argparse.Namespace) -> VoucherContext:
    if args.existing_voucher_id:
        sync_seckill_stock(
            redis_container=args.redis_container,
            redis_password=args.redis_password,
            redis_db=args.redis_db,
            voucher_id=args.existing_voucher_id,
            stock=args.stock,
        )
        return VoucherContext(
            voucher_id=str(args.existing_voucher_id),
            title=args.voucher_title or "Existing voucher",
            stock=args.stock,
            begin_time="existing",
            end_time="existing",
            created=False,
        )
    return create_seckill_voucher(args, datetime.now())


def login(base_url: str, email: str, password: str, timeout: int) -> tuple[str, str]:
    url = f"{base_url.rstrip('/')}/user/login"
    body = json_request(url, {"email": email, "password": password}, timeout)

    if not body.get("success"):
        raise RuntimeError(f"Login failed for {email}: {body.get('errorMsg')}")

    data = body.get("data") or {}
    access_token = data.get("access_token")
    refresh_token = data.get("refresh_token")
    if not access_token:
        raise RuntimeError(f"Login succeeded for {email} but access_token was missing")
    return access_token, refresh_token or ""


def login_many(base_url: str, emails: List[str], password: str, timeout: int, workers: int) -> tuple[List[str], List[str]]:
    def login_one(email: str) -> tuple[str, str]:
        return login(base_url, email, password, timeout)

    with ThreadPoolExecutor(max_workers=workers) as executor:
        results = list(executor.map(login_one, emails))

    access_tokens = [item[0] for item in results]
    refresh_tokens = [item[1] for item in results]
    return access_tokens, refresh_tokens


def write_outputs(
    output_dir: Path,
    emails: List[str],
    access_tokens: List[str],
    refresh_tokens: List[str],
    api_base_url: str,
    k6_base_url: str,
    voucher: VoucherContext,
    count: int,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)

    csv_path = output_dir / "tokens.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(["email", "access_token", "refresh_token"])
        for row in zip(emails, access_tokens, refresh_tokens):
            writer.writerow(row)

    txt_path = output_dir / "auth_tokens.txt"
    txt_path.write_text("\n".join(access_tokens) + "\n", encoding="utf-8")

    ps1_path = output_dir / "set-auth-tokens.ps1"
    ps1_path.write_text(
        textwrap.dedent(
            f"""
            $env:API_BASE_URL = "{api_base_url}"
            $env:BASE_URL = "{k6_base_url}"
            $env:VOUCHER_ID = "{voucher.voucher_id}"
            $env:AUTH_TOKENS_FILE = "/scripts/generated/auth_tokens.txt"
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    run_ps1_path = output_dir / "run-k6.ps1"
    run_ps1_path.write_text(
        textwrap.dedent(
            f"""
            $ErrorActionPreference = 'Stop'
            $env:BASE_URL = "{k6_base_url}"
            $env:VOUCHER_ID = "{voucher.voucher_id}"
            $env:AUTH_TOKENS_FILE = "/scripts/generated/auth_tokens.txt"

            $backendDir = (Resolve-Path (Join-Path $PSScriptRoot '..\\..\\..')).Path
            Push-Location $backendDir
            try {{
                docker compose --profile loadtest run --rm k6 run -o experimental-prometheus-rw /scripts/seckill.js
            }}
            finally {{
                Pop-Location
            }}
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    burn_ps1_path = output_dir / "run-k6-burn.ps1"
    burn_ps1_path.write_text(
        textwrap.dedent(
            f"""
            $ErrorActionPreference = 'Stop'
            $env:BASE_URL = "{k6_base_url}"
            $env:VOUCHER_ID = "{voucher.voucher_id}"
            $env:AUTH_TOKENS_FILE = "/scripts/generated/auth_tokens.txt"
            $env:K6_SCENARIO_MODE = "ramping-arrival-rate"
            $env:K6_START_RATE = "300"
            $env:K6_PRE_ALLOCATED_VUS = "500"
            $env:K6_MAX_VUS = "5000"
            $env:K6_STAGES = "15s:500,15s:1200,30s:2500,30s:4000,20s:0"
            $env:K6_SLEEP_MS = "0"

            $backendDir = (Resolve-Path (Join-Path $PSScriptRoot '..\\..\\..')).Path
            Push-Location $backendDir
            try {{
                docker compose --profile loadtest run --rm k6 run -o experimental-prometheus-rw /scripts/seckill.js
            }}
            finally {{
                Pop-Location
            }}
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    context_path = output_dir / "loadtest-context.json"
    context_path.write_text(
        json.dumps(
            {
                "generatedAt": datetime.now().isoformat(timespec="seconds"),
                "apiBaseUrl": api_base_url,
                "k6BaseUrl": k6_base_url,
                "voucher": {
                    "id": voucher.voucher_id,
                    "title": voucher.title,
                    "stock": voucher.stock,
                    "beginTime": voucher.begin_time,
                    "endTime": voucher.end_time,
                    "created": voucher.created,
                },
                "userCount": count,
                "generatedFiles": {
                    "tokensCsv": str(csv_path),
                    "authTokensFile": str(txt_path),
                    "envScript": str(ps1_path),
                    "runK6Script": str(run_ps1_path),
                    "runK6BurnScript": str(burn_ps1_path),
                },
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    next_steps = output_dir / "next-steps.txt"
    next_steps.write_text(
        textwrap.dedent(
            f"""
            1. Confirm the backend is running at {api_base_url}
            2. Review the generated voucher id: {voucher.voucher_id}
            3. Run:
               powershell -ExecutionPolicy Bypass -File "{run_ps1_path}"
            4. Stress test harder:
               powershell -ExecutionPolicy Bypass -File "{burn_ps1_path}"
            5. Open:
               Prometheus: http://localhost:9090
               Grafana: http://localhost:3000
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    default_output_dir = Path(__file__).resolve().parents[1] / "k6" / "generated"

    parser = argparse.ArgumentParser(
        description="Create a seckill voucher, export login tokens, and generate k6 commands"
    )
    parser.add_argument(
        "--api-base-url",
        "--base-url",
        dest="api_base_url",
        default="http://localhost:8081",
        help="Backend base URL used for login requests",
    )
    parser.add_argument(
        "--k6-base-url",
        default="http://host.docker.internal:8081",
        help="Backend base URL used by dockerized k6",
    )
    parser.add_argument("--count", type=int, default=120, help="How many load-test users to prepare")
    parser.add_argument("--start-index", type=int, default=1, help="Email suffix start index")
    parser.add_argument("--prefix", default="loadtest", help="Email prefix for generated users")
    parser.add_argument("--domain", default="example.com", help="Email domain for generated users")
    parser.add_argument("--password", default="loadtest123", help="Shared password for load-test users")
    parser.add_argument("--nickname-prefix", default="loadtest-user-", help="Nickname prefix")
    parser.add_argument("--timeout", type=int, default=10, help="HTTP timeout in seconds")
    parser.add_argument("--mysql-batch-size", type=int, default=500, help="How many users to upsert per MySQL batch")
    parser.add_argument("--login-workers", type=int, default=32, help="How many concurrent login requests to run")

    parser.add_argument("--existing-voucher-id", help="Reuse an existing voucher id instead of creating one")
    parser.add_argument("--voucher-title", help="Voucher title")
    parser.add_argument("--voucher-sub-title", help="Voucher subtitle")
    parser.add_argument("--voucher-rules", help="Voucher rules text")
    parser.add_argument("--pay-value", type=int, default=4750, help="Voucher pay value in cents")
    parser.add_argument("--actual-value", type=int, default=5000, help="Voucher actual value in cents")
    parser.add_argument("--stock", type=int, default=1000, help="Voucher stock and Redis seckill stock")
    parser.add_argument(
        "--begin-offset-minutes",
        type=int,
        default=5,
        help="Created voucher starts this many minutes in the past",
    )
    parser.add_argument(
        "--duration-minutes",
        type=int,
        default=180,
        help="Created voucher remains valid for this many minutes from now",
    )

    parser.add_argument("--mysql-container", default="market-mysql", help="MySQL container name")
    parser.add_argument("--mysql-user", default="root", help="MySQL username")
    parser.add_argument("--mysql-password", default="1234", help="MySQL password")
    parser.add_argument("--db-name", default="market", help="MySQL database name")

    parser.add_argument("--redis-container", default="market-redis", help="Redis container name")
    parser.add_argument("--redis-password", default="1234", help="Redis password")
    parser.add_argument("--redis-db", type=int, default=6, help="Redis database index used by the backend")

    parser.add_argument("--output-dir", default=str(default_output_dir), help="Output directory")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    emails = build_emails(args.prefix, args.domain, args.count, args.start_index)
    output_dir = Path(args.output_dir).resolve()

    print("[1/4] Preparing seckill voucher...")
    voucher = build_voucher_context(args)

    print(f"[2/4] Upserting {len(emails)} load-test users into MySQL...")
    ensure_users(
        container=args.mysql_container,
        db_name=args.db_name,
        mysql_user=args.mysql_user,
        mysql_password=args.mysql_password,
        emails=emails,
        password=args.password,
        nickname_prefix=args.nickname_prefix,
        start_index=args.start_index,
        batch_size=args.mysql_batch_size,
    )

    print("[3/4] Logging in users and fetching access tokens...")
    access_tokens, refresh_tokens = login_many(
        args.api_base_url,
        emails,
        args.password,
        args.timeout,
        args.login_workers,
    )

    print("[4/4] Writing k6-ready files...")
    write_outputs(
        output_dir=output_dir,
        emails=emails,
        access_tokens=access_tokens,
        refresh_tokens=refresh_tokens,
        api_base_url=args.api_base_url,
        k6_base_url=args.k6_base_url,
        voucher=voucher,
        count=args.count,
    )

    print()
    print("Completed.")
    print(f"- Voucher ID: {voucher.voucher_id} ({'created' if voucher.created else 'reused'})")
    print(f"- Token CSV: {output_dir / 'tokens.csv'}")
    print(f"- Token file: {output_dir / 'auth_tokens.txt'}")
    print(f"- Env script: {output_dir / 'set-auth-tokens.ps1'}")
    print(f"- Run script: {output_dir / 'run-k6.ps1'}")
    print(f"- Burn script: {output_dir / 'run-k6-burn.ps1'}")
    print()
    print("Next command:")
    print(f'powershell -ExecutionPolicy Bypass -File "{output_dir / "run-k6.ps1"}"')
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"Execution failed: {exc}", file=sys.stderr)
        raise SystemExit(exc.returncode)
    except Exception as exc:
        print(f"Execution failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
