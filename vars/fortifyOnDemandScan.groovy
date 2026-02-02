/**
 * Starts (and optionally waits for) a Fortify on Demand static scan using FoD v3 APIs.
 *
 * Expected credentials (provided by withFortifySecrets):
 * - FORTIFY_USER_NAME (OAuth client_id)
 * - FORTIFY_PASSWORD  (OAuth client_secret)
 *
 * Release resolution order:
 * 1) params.releaseId
 * 2) env.FORTIFY_RELEASE_ID
 * 3) config/fortify-client.properties (fortify.client.releaseId)
 * 4) FoD release lookup by name (params.releaseName / env.COMPONENT / repo name derived from env.GIT_URL)
 */
def call(Map params = [:]) {
  String apiBaseUrl = (params.apiBaseUrl ?: env.FORTIFY_API_BASE_URL ?: 'https://api.emea.fortify.com').toString().trim()
  String portalBaseUrl = (params.portalBaseUrl ?: env.FORTIFY_PORTAL_BASE_URL ?: 'https://emea.fortify.com').toString().trim()
  String scope = (params.scope ?: env.FORTIFY_OAUTH_SCOPE ?: 'api-tenant').toString().trim()

  String releaseId = (params.releaseId ?: env.FORTIFY_RELEASE_ID ?: resolveReleaseIdFromProperties((params.releasePropertiesPath ?: 'config/fortify-client.properties').toString()) ?: '').toString().trim()
  String repoName = (params.repoName ?: deriveRepoNameFromGitUrl(env.GIT_URL ?: '')).toString().trim()
  // Prefer repo name for release lookup (component is often a generic value like "frontend"/"api").
  String releaseName = (params.releaseName ?: repoName ?: env.COMPONENT ?: '').toString().trim()

  int pollAttempts = (params.containsKey('scanPollAttempts') ? (params.scanPollAttempts as int) : (env.FORTIFY_SCAN_POLL_ATTEMPTS ?: 60)) as int
  int pollSleepSeconds = (params.containsKey('scanPollSleepSeconds') ? (params.scanPollSleepSeconds as int) : (env.FORTIFY_SCAN_POLL_SLEEP_SECONDS ?: 10)) as int
  boolean failOnTimeout = ((params.failOnTimeout ?: env.FORTIFY_SCAN_FAIL_ON_TIMEOUT ?: 'false').toString().trim().toLowerCase() == 'true')

  sh "mkdir -p 'Fortify Scan'"

  String bashTemplate = '''#!/usr/bin/env bash
set +x
set -euo pipefail

API_BASE_URL='__API_BASE_URL__'
PORTAL_BASE_URL='__PORTAL_BASE_URL__'
SCOPE='__SCOPE__'
RELEASE_ID='__RELEASE_ID__'
RELEASE_NAME='__RELEASE_NAME__'
REPO_NAME='__REPO_NAME__'
POLL_ATTEMPTS='__POLL_ATTEMPTS__'
POLL_SLEEP_SECONDS='__POLL_SLEEP_SECONDS__'
FAIL_ON_TIMEOUT='__FAIL_ON_TIMEOUT__'

PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python

report_path='Fortify Scan/FortifyScanReport.html'
status='Unknown'
scan_id=''
error=''

tmpdir=''

cleanup() {
  if [ -n "$tmpdir" ] && [ -d "$tmpdir" ]; then
    rm -rf "$tmpdir" || true
  fi

  cat > "$report_path" <<EOF
<!doctype html>
<meta charset="utf-8">
<title>Fortify Scan</title>
<pre>
releaseId=$RELEASE_ID
releaseName=$RELEASE_NAME
repo=$REPO_NAME
scanId=$scan_id
status=$status
error=$error
portal=$PORTAL_BASE_URL/Releases/$RELEASE_ID/Issues
</pre>
EOF
}
trap cleanup EXIT

client_id="\${FORTIFY_USER_NAME:-}"
client_secret="\${FORTIFY_PASSWORD:-}"
if [ -z "$client_id" ] || [ -z "$client_secret" ]; then
  error='missing FoD credentials (expected FORTIFY_USER_NAME/FORTIFY_PASSWORD from Jenkins credentials binding)'
  echo "Fortify: $error"
  exit 1
fi

token_combined="$(curl -sS -X POST "$API_BASE_URL/oauth/token" \\
  -H 'Content-Type: application/x-www-form-urlencoded' \\
  --data-urlencode 'grant_type=client_credentials' \\
  --data-urlencode "scope=$SCOPE" \\
  --data-urlencode "client_id=$client_id" \\
  --data-urlencode "client_secret=$client_secret" \\
  -w '\\n%{http_code}' || true)"

token_body="$(printf '%s' "$token_combined" | sed '$d')"
token_code="$(printf '%s' "$token_combined" | tail -n 1 | tr -d '\\r')"
if [ -z "$token_code" ] || [ "$token_code" -lt 200 ] || [ "$token_code" -ge 300 ]; then
  error="failed to obtain access_token from /oauth/token (HTTP \${token_code:-unknown})"
  echo "Fortify: $error"
  exit 1
fi

token="$(printf '%s' "$token_body" | "$PY" -c 'import sys,json
raw=sys.stdin.read().strip()
try:
  print((json.loads(raw).get(\"access_token\") or \"\") if raw else \"\")
except Exception:
  print(\"\")')"

if [ -z "$token" ]; then
  error='failed to parse access_token from /oauth/token response'
  echo "Fortify: $error"
  exit 1
fi

if [ -z "$RELEASE_ID" ]; then
  candidate="$RELEASE_NAME"
  if [ -z "$candidate" ]; then
    candidate="$REPO_NAME"
  fi
  if [ -z "$candidate" ]; then
    error='unable to determine releaseId (set FORTIFY_RELEASE_ID or provide config/fortify-client.properties)'
    echo "Fortify: $error"
    exit 1
  fi

  echo "Fortify: resolving releaseId for '$candidate'..."

  offset=0
  # FoD v3 endpoints frequently cap limit to 50.
  limit=50
  while [ "$offset" -le 2000 ]; do
    releases_combined="$(curl -sS -G "$API_BASE_URL/api/v3/releases" \\
      -H "Authorization: Bearer $token" \\
      -H 'Accept: application/json' \\
      --data-urlencode "offset=$offset" \\
      --data-urlencode "limit=$limit" \\
      -w '\\n%{http_code}' || true)"

    releases_body="$(printf '%s' "$releases_combined" | sed '$d')"
    releases_code="$(printf '%s' "$releases_combined" | tail -n 1 | tr -d '\\r')"
    if [ -z "$releases_code" ] || [ "$releases_code" -lt 200 ] || [ "$releases_code" -ge 300 ]; then
      body_snippet="$(printf '%s' "$releases_body" | head -c 500 | tr '\\n' ' ')"
      error="failed to list releases (HTTP \${releases_code:-unknown}) \${body_snippet:+- $body_snippet}"
      echo "Fortify: $error"
      exit 1
    fi

    found="$(
      printf '%s' "$releases_body" | "$PY" -c 'import sys,json
candidate=sys.argv[1].strip().lower()
try:
  j=json.load(sys.stdin)
except Exception:
  j={}
items=j.get(\"items\") or j.get(\"data\") or []
def name_of(x):
  return (x.get(\"releaseName\") or x.get(\"name\") or x.get(\"release\") or \"\").strip()
def id_of(x):
  v=x.get(\"releaseId\")
  if v is None: v=x.get(\"id\")
  if v is None: v=x.get(\"release_id\")
  return \"\" if v is None else str(v).strip()
for it in items:
  n=name_of(it).lower()
  if n and n==candidate:
    print(id_of(it)); sys.exit(0)
print(\"\")' "$candidate"
    )"

    if [ -n "$found" ]; then
      RELEASE_ID="$found"
      echo "Fortify: resolved releaseId=$RELEASE_ID"
      break
    fi

    count="$(
      printf '%s' "$releases_body" | "$PY" -c 'import sys,json
try:
  j=json.load(sys.stdin)
except Exception:
  j={}
items=j.get(\"items\") or j.get(\"data\") or []
print(len(items))'
    )"

    if [ "$count" -lt "$limit" ]; then
      break
    fi
    offset=$((offset+limit))
  done

  if [ -z "$RELEASE_ID" ]; then
    error="unable to resolve releaseId for '$candidate' (ensure FoD release name matches repo/component or set FORTIFY_RELEASE_ID)"
    echo "Fortify: $error"
    exit 1
  fi
fi

tmpdir="$(mktemp -d)"
zipfile="$tmpdir/fortify-source.zip"

if ! ZIP_OUT="$zipfile" "$PY" - <<'PY'
import os
import subprocess
import zipfile

zip_path = os.environ.get("ZIP_OUT")
if not zip_path:
  raise SystemExit("missing ZIP_OUT")

exclude_dir_names = {
  ".git",
  "Fortify Scan",
  "node_modules",
  ".yarn",
  ".gradle",
  "build",
  "dist",
  "target",
  ".terraform",
  ".scannerwork",
  ".sonar",
  ".idea",
  ".vscode",
  "__pycache__",
  ".pytest_cache",
  ".mypy_cache",
  ".ruff_cache",
  ".tox",
  ".venv",
  "venv",
}

exclude_file_suffixes = (
  ".jar",
  ".zip",
  ".tar",
  ".tgz",
  ".tar.gz",
  ".key",
  ".pem",
  ".pfx",
)

exclude_files = {
  ".pnp.cjs",
  ".pnp.loader.mjs",
  ".yarn/install-state.gz",
  ".yarn/build-state.yml",
}

def normalize(path: str) -> str:
  norm = (path or "").replace(os.sep, "/")
  if norm.startswith("./"):
    norm = norm[2:]
  return norm.strip("/")

def has_excluded_dir(path: str) -> bool:
  norm = normalize(path)
  if not norm:
    return False
  parts = [p for p in norm.split("/") if p]
  return any(p in exclude_dir_names for p in parts)

def should_exclude_file(path: str) -> bool:
  norm = normalize(path)
  if not norm:
    return False
  if norm in exclude_files:
    return True
  if has_excluded_dir(os.path.dirname(norm)):
    return True
  lower = norm.lower()
  return any(lower.endswith(s) for s in exclude_file_suffixes)

def list_git_tracked_files():
  if not os.path.isdir(".git"):
    return None
  try:
    out = subprocess.check_output(["git", "ls-files", "-z"], stderr=subprocess.DEVNULL)
    raw_paths = [p for p in out.split(b"\\0") if p]
    paths = [p.decode("utf-8", errors="surrogateescape") for p in raw_paths]
    return paths or None
  except Exception:
    return None

def list_files_fallback():
  files = []
  for root, dirs, filenames in os.walk(".", topdown=True):
    rel_root = os.path.relpath(root, ".")
    if rel_root == ".":
      rel_root = ""
    dirs[:] = [d for d in dirs if not has_excluded_dir(os.path.join(rel_root, d) if rel_root else d)]
    for f in filenames:
      rel = os.path.join(rel_root, f) if rel_root else f
      files.append(rel)
  return files

paths = list_git_tracked_files() or list_files_fallback()
with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
  for rel in paths:
    rel_norm = normalize(rel)
    if should_exclude_file(rel_norm):
      continue
    if not os.path.isfile(rel_norm):
      continue
    z.write(rel_norm, arcname=rel_norm)
PY
then
  error='failed to create source archive'
  echo "Fortify: $error"
  exit 1
fi

zip_size_mib="$("$PY" -c 'import os,sys; print(f"{os.path.getsize(sys.argv[1]) / 1024 / 1024:.2f}")' "$zipfile")"
echo "Fortify: created source archive (${zip_size_mib} MiB)"

setup_combined="$(curl -sS -X GET \\
  "$API_BASE_URL/api/v3/releases/$RELEASE_ID/static-scans/scan-setup" \\
  -H "Authorization: Bearer $token" \\
  -H 'Accept: application/json' \\
  -w '\\n%{http_code}' || true)"

setup_body="$(printf '%s' "$setup_combined" | sed '$d')"
setup_code="$(printf '%s' "$setup_combined" | tail -n 1 | tr -d '\\r')"
if [ -z "$setup_code" ] || [ "$setup_code" -lt 200 ] || [ "$setup_code" -ge 300 ]; then
  error="failed to fetch static scan setup (HTTP \${setup_code:-unknown})"
  echo "Fortify: $error"
  exit 1
fi

assessment_type_id="$(printf '%s' "$setup_body" | "$PY" -c 'import sys,json; j=json.load(sys.stdin); print(j.get(\"assessmentTypeId\") or \"\")')"
entitlement_id="$(printf '%s' "$setup_body" | "$PY" -c 'import sys,json; j=json.load(sys.stdin); print(j.get(\"entitlementId\") or \"\")')"
entitlement_frequency_type="$(printf '%s' "$setup_body" | "$PY" -c 'import sys,json; j=json.load(sys.stdin); print(j.get(\"entitlementFrequencyType\") or \"\")')"

if [ -z "$assessment_type_id" ] || [ -z "$entitlement_id" ] || [ -z "$entitlement_frequency_type" ]; then
  error='invalid static scan setup response (missing required fields)'
  echo "Fortify: $error"
  exit 1
fi

scan_tool='hmcts-jenkins'
scan_tool_version='cnp-jenkins-library'

start_combined="$(curl -sS -X POST \\
  "$API_BASE_URL/api/v3/releases/$RELEASE_ID/static-scans/start-scan?assessmentTypeId=${assessment_type_id}&entitlementId=${entitlement_id}&entitlementFrequencyType=${entitlement_frequency_type}&fragNo=-1&offset=0&scanTool=$scan_tool&scanToolVersion=$scan_tool_version" \\
  -H "Authorization: Bearer $token" \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/octet-stream' \\
  --data-binary "@$zipfile" \\
  -w '\\n%{http_code}' || true)"

start_body="$(printf '%s' "$start_combined" | sed '$d')"
start_code="$(printf '%s' "$start_combined" | tail -n 1 | tr -d '\\r')"
if [ -z "$start_code" ] || [ "$start_code" -lt 200 ] || [ "$start_code" -ge 300 ]; then
  error="failed to start static scan (HTTP \${start_code:-unknown})"
  echo "Fortify: $error"
  exit 1
fi

scan_id="$(printf '%s' "$start_body" | "$PY" -c 'import sys,json
try:
  j=json.load(sys.stdin)
  v=j.get(\"scanId\")
  print(\"\" if v is None else str(v))
except Exception:
  print(\"\")')"

if [ -z "$scan_id" ]; then
  error='failed to start static scan (missing scanId)'
  echo "Fortify: $error"
  exit 1
fi

echo "Fortify: scan requested (releaseId=$RELEASE_ID, scanId=$scan_id)"
status='Queued'

attempts="$POLL_ATTEMPTS"
sleep_s="$POLL_SLEEP_SECONDS"
if [ -z "$attempts" ]; then attempts=60; fi
if [ -z "$sleep_s" ]; then sleep_s=10; fi

last_status=''
for i in $(seq 1 "$attempts"); do
  scan_combined="$(curl -sS -X GET \\
    "$API_BASE_URL/api/v3/releases/$RELEASE_ID/scans/$scan_id" \\
    -H "Authorization: Bearer $token" \\
    -H 'Accept: application/json' \\
    -w '\\n%{http_code}' || true)"

  scan_body="$(printf '%s' "$scan_combined" | sed '$d')"
  scan_code="$(printf '%s' "$scan_combined" | tail -n 1 | tr -d '\\r')"

  if [ -z "$scan_code" ] || [ "$scan_code" -lt 200 ] || [ "$scan_code" -ge 300 ]; then
    if [ "$scan_code" = "404" ]; then
      # FoD can return 404 briefly after starting a scan; fall back to the scans list.
      status='Queued'

      list_combined="$(curl -sS -X GET \\
        "$API_BASE_URL/api/v3/releases/$RELEASE_ID/scans?orderBy=startedDateTime&orderByDirection=DESC&limit=20&fields=scanId,analysisStatusType,scanType,startedDateTime,completedDateTime" \\
        -H "Authorization: Bearer $token" \\
        -H 'Accept: application/json' \\
        -w '\\n%{http_code}' || true)"

      list_body="$(printf '%s' "$list_combined" | sed '$d')"
      list_code="$(printf '%s' "$list_combined" | tail -n 1 | tr -d '\\r')"
      if [ -n "$list_code" ] && [ "$list_code" -ge 200 ] && [ "$list_code" -lt 300 ]; then
        status="$(
          printf '%s' "$list_body" | "$PY" -c 'import sys,json
scan_id=sys.argv[1]
def norm(v):
  if v is None:
    return ""
  if isinstance(v, dict):
    for k in ("name","value","type","displayName","text","code"):
      if k in v and v[k] is not None:
        return str(v[k]).strip()
    return str(v).strip()
  return str(v).strip()
try:
  j=json.load(sys.stdin)
  items=j.get("items") or j.get("data") or []
  for it in items:
    if str(it.get("scanId") or it.get("id") or "") == scan_id:
      print(norm(it.get("analysisStatusType")) or "Queued")
      sys.exit(0)
  print("Queued")
except Exception:
  print("Queued")' "$scan_id"
        )"
      fi

      if [ "$status" != "$last_status" ]; then
        echo "Fortify: scanId=$scan_id status=$status"
        last_status="$status"
      fi

      if [ "$status" = "Completed" ] || [ "$status" = "Failed" ] || [ "$status" = "Canceled" ]; then
        break
      fi
      sleep "$sleep_s"
      continue
    fi

    error="failed to poll scan status (HTTP \${scan_code:-unknown})"
    echo "Fortify: $error"
    status='Unknown'
    break
  fi

  status="$(printf '%s' "$scan_body" | "$PY" -c 'import sys,json
def norm(v):
  if v is None:
    return ""
  if isinstance(v, dict):
    for k in ("name","value","type","displayName","text","code"):
      if k in v and v[k] is not None:
        return str(v[k]).strip()
    return str(v).strip()
  return str(v).strip()
try:
  j=json.load(sys.stdin)
  print(norm(j.get(\"analysisStatusType\")) or \"Unknown\")
except Exception:
  print(\"Unknown\")')"

  if [ "$status" != "$last_status" ]; then
    echo "Fortify: scanId=$scan_id status=$status"
    last_status="$status"
  fi

  if [ "$status" = "Completed" ] || [ "$status" = "Failed" ] || [ "$status" = "Canceled" ]; then
    break
  fi
  sleep "$sleep_s"
done

if [ "$status" != "Completed" ]; then
  if [ "$FAIL_ON_TIMEOUT" = "true" ]; then
    error=${error:-"scan did not complete successfully (status=$status)"}
    echo "Fortify: $error"
    exit 1
  fi
  echo "Fortify: scan not completed yet (status=$status); vulnerability details may appear on a subsequent build"
fi
'''

  String bash = bashTemplate
    .replace('__API_BASE_URL__', escapeForBash(apiBaseUrl))
    .replace('__PORTAL_BASE_URL__', escapeForBash(portalBaseUrl))
    .replace('__SCOPE__', escapeForBash(scope))
    .replace('__RELEASE_ID__', escapeForBash(releaseId))
    .replace('__RELEASE_NAME__', escapeForBash(releaseName))
    .replace('__REPO_NAME__', escapeForBash(repoName))
    .replace('__POLL_ATTEMPTS__', pollAttempts.toString())
    .replace('__POLL_SLEEP_SECONDS__', pollSleepSeconds.toString())
    .replace('__FAIL_ON_TIMEOUT__', failOnTimeout ? 'true' : 'false')

  sh(label: 'Fortify FoD scan', script: bash)
}

private String resolveReleaseIdFromProperties(String propertiesPath) {
  String path = (propertiesPath ?: '').toString()
  if (!path) {
    return null
  }
  if (!fileExists(path)) {
    return null
  }
  String text = readFile(path) ?: ''
  def matcher = (text =~ /(?m)^\s*fortify\.client\.releaseId\s*=\s*(\d+)\s*$/)
  if (!matcher.find()) {
    return null
  }
  return matcher.group(1)
}

private String deriveRepoNameFromGitUrl(String gitUrl) {
  String url = (gitUrl ?: '').toString().trim()
  if (!url) {
    return ''
  }
  // Examples:
  // - https://github.com/hmcts/cnp-plum-frontend.git
  // - git@github.com:hmcts/cnp-plum-frontend.git
  String noGit = url.endsWith('.git') ? url.substring(0, url.length() - 4) : url
  String last = noGit.tokenize('/').last()
  if (last.contains(':')) {
    last = last.tokenize(':').last()
  }
  return last ?: ''
}

private static String escapeForBash(String value) {
  return (value ?: '').replace("'", "'\"'\"'")
}
