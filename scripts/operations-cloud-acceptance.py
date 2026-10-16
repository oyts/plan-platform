#!/usr/bin/env python3
"""Synthetic, isolated MySQL acceptance; never reads production user records."""
import datetime, json, os, pathlib, re, subprocess, urllib.request, urllib.error, uuid

directory = pathlib.Path.cwd().resolve()
if not re.fullmatch(r"/home/[a-zA-Z0-9_-]+/plan-platform/ai-candidates/0\.1\.13-[a-zA-Z0-9_-]+", str(directory)):
    raise SystemExit("Run only in the new isolated operations candidate")
started = directory / "operations-acceptance-started.json"
with started.open("x") as stream:
    json.dump({"startedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "paidCalls": 0}, stream)
settings = {}
for filename in (".env", "candidate.env"):
    for line in (directory / filename).read_text().splitlines():
        key, value = line.split("=", 1)
        settings[key] = value
database = settings["PLAN_MYSQL_DATABASE"]
assert re.fullmatch(r"plan_acceptance_[0-9]+_[a-f0-9]+", database)
credentials = json.loads((directory / "ai-acceptance-private.json").read_text())["admin"]
origin = "http://127.0.0.1:43144/api"
token = None
checks = []

def api(path, data=None, method=None, expected=200, bearer=True):
    headers = {"Content-Type": "application/json"}
    if token and bearer:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(origin + path, None if data is None else json.dumps(data).encode(), headers, method=method or ("GET" if data is None else "POST"))
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    assert response.code == expected, (path.split("?")[0], response.code, expected)
    body = response.read()
    return (json.loads(body) if body else None), response.headers

report = {"version": "0.1.13", "paidCalls": 0, "status": "FAIL", "checks": checks}
try:
    health, _ = api("/health", bearer=False)
    assert health["version"] == "0.1.13"
    registered, _ = api("/auth/register", {**credentials, "displayName": "Synthetic operations admin"}, expected=201, bearer=False)
    assert registered["user"]["role"] == "ADMIN"
    token = registered["accessToken"]
    owner = registered["user"]["id"]
    assert re.fullmatch(r"[a-f0-9-]{36}", owner)
    _, headers = api("/admin/mail")
    assert "no-store" in headers["Cache-Control"]
    status, _ = api("/admin/mail")
    assert status["configured"] is False and status["passwordResetEnabled"] is False
    checks.append("SMTP absent is reported honestly; recovery remains disabled")
    day = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=8)).date().isoformat()
    sql = []
    for quota_state, terminal, input_tokens, output_tokens, failure in (
        ("SUCCEEDED", "'SUCCEEDED'", "120", "45", "NULL"),
        ("RELEASED", "'FAILED'", "NULL", "NULL", "'BUSY'"),
        ("UNKNOWN", "'UNKNOWN'", "NULL", "NULL", "'TIMEOUT'"),
        ("SUCCEEDED", "NULL", "NULL", "NULL", "NULL"),
    ):
        key = str(uuid.uuid4())
        sql.append("INSERT INTO ai_quota_requests(id,owner_id,operation_key,fingerprint,quota_day,state,created_at,updated_at,terminal_state,input_tokens,output_tokens,failure_code) VALUES "
                   f"('{key}','{owner}','{key}','{'a'*64}','{day}','{quota_state}',NOW(6),NOW(6),{terminal},{input_tokens},{output_tokens},{failure});")
    environment = {**os.environ, "MYSQL_PWD": settings["MYSQL_ROOT_PASSWORD"]}
    result = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", settings["AI_MYSQL_CONTAINER"], "mysql", "-uroot", database], input="\n".join(sql).encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=environment)
    assert result.returncode == 0, "Synthetic SQL fixture failed; output withheld"
    usage, headers = api("/admin/ai/usage?from=" + day + "&to=" + day + "&size=2")
    assert "no-store" in headers["Cache-Control"]
    assert usage["totals"] == dict(requests=4, succeeded=2, failed=1, unknown=1, queued=0, running=0, untracked=0, inputTokens=120, outputTokens=45, tokenKnownRequests=1)
    assert usage["recent"]["total"] == 4 and len(usage["recent"]["items"]) == 2
    assert usage["runtime"]["enabled"] is False
    assert "password" not in json.dumps(usage) and "sessionId" not in json.dumps(usage)
    api("/admin/ai/usage?size=51", expected=400)
    checks.append("MySQL metadata totals, nullable legacy tokens, pagination, privacy and range validation")
    email = "operations-member-" + uuid.uuid4().hex + "@example.test"
    old, _ = api("/admin/invitations", {"email": email, "expiresInHours": 24}, expected=201)
    new, _ = api("/admin/invitations", {"email": email, "expiresInHours": 24}, expected=201)
    from urllib.parse import urlparse, parse_qs
    old_token = parse_qs(urlparse(old["registrationUrl"]).query)["invite"][0]
    new_token = parse_qs(urlparse(new["registrationUrl"]).query)["invite"][0]
    password = uuid.uuid4().hex + "A1"
    body = {"email": email, "password": password, "displayName": "Synthetic operations member", "invitationToken": old_token}
    api("/auth/register", body, expected=403, bearer=False)
    body["invitationToken"] = new_token
    member, _ = api("/auth/register", body, expected=201, bearer=False)
    assert member["user"]["role"] == "USER"
    token = member["accessToken"]
    for path in ("/admin/mail", "/admin/ai/usage", "/admin/ai/settings"):
        api(path, expected=403)
    checks.append("Rotated invitations reject old link; new member cannot read administrator mail/usage/settings")
    report["status"] = "PASS"
    print("PASS: isolated MySQL operations, invitations and admin boundaries; 0 paid calls; production untouched")
except Exception as error:
    report["failure"] = type(error).__name__
    raise SystemExit("Operations acceptance failed; private candidate retained; no secrets printed") from None
finally:
    (directory / "operations-acceptance.json").write_text(json.dumps(report, indent=2))
