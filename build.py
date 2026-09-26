import os
import sys
import time
import json
import getpass
import subprocess
import urllib.request
import urllib.error
import http.client

FIXED_REPO_NAME = "Mobilelock"

def run_command(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result

def api_request(url, token, method="GET", data=None):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "SilentHost-Builder"
    }
    req = urllib.request.Request(url, headers=headers, method=method)
    if data:
        req.data = json.dumps(data).encode("utf-8")
        req.add_header("Content-Type", "application/json")
    last_error = None
    for attempt in range(1, 5):
        try:
            with urllib.request.urlopen(req, timeout=45) as resp:
                raw = resp.read()
                return json.loads(raw.decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            try:
                detail = e.read().decode("utf-8", errors="replace")
            except Exception:
                detail = str(e)
            print(f"\n[ERROR] GitHub API ({e.code}): {detail}")
            sys.exit(1)
        except (http.client.IncompleteRead, urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last_error = e
            if attempt < 4:
                wait = attempt * 2
                print(f"\n[WARN] Retrying in {wait}s... (attempt {attempt}/4)")
                time.sleep(wait)
            else:
                print(f"\n[ERROR] GitHub API request failed after 4 attempts: {e}")
                sys.exit(1)
    raise last_error

def ensure_repo_exists(username, token):
    repo_url = f"https://api.github.com/repos/{username}/{FIXED_REPO_NAME}"
    existing = api_request(repo_url, token)
    if existing:
        print(f"-> Using existing repository: {existing['html_url']}")
        return existing
    print(f"-> Creating new repository '{FIXED_REPO_NAME}'...")
    payload = {"name": FIXED_REPO_NAME, "private": True}
    created = api_request("https://api.github.com/user/repos", token, method="POST", data=payload)
    print(f"-> Repository Created: {created['html_url']}")
    return created

def main():
    cwd = os.getcwd()
    print("==================================================")
    print("        PRANKLOCK - GITHUB AUTO BUILDER           ")
    print("==================================================")
    print(f"Working Directory: {cwd}\n")

    token = getpass.getpass("Enter GitHub Token (hidden): ").strip()
    if not token:
        print("[ERROR] Token cannot be empty.")
        sys.exit(1)

    print("\n[Step 1/4] Verifying GitHub Account & Repository...")
    user_info = api_request("https://api.github.com/user", token)
    username = user_info.get("login")
    print(f"-> Authenticated User: {username}")

    ensure_repo_exists(username, token)

    print("\n[Step 2/4] Preparing project files...")
    for root, dirs, files in os.walk(cwd):
        if ".git" in dirs:
            dirs.remove(".git")
        for file in files:
            if file == ".gitignore":
                ign_path = os.path.join(root, file)
                try:
                    with open(ign_path, "r", encoding="utf-8", errors="ignore") as f:
                        lines = f.readlines()
                    filtered = [l for l in lines if not any(k in l.lower() for k in ["keystore", "jks", "key.properties"])]
                    with open(ign_path, "w", encoding="utf-8") as f:
                        f.writelines(filtered)
                except Exception:
                    pass

    with open(".gitignore", "w", encoding="utf-8") as f:
        f.write("build/\n.gradle/\n.idea/\n*.apk\n.env\n")

    os.makedirs(".github/workflows", exist_ok=True)

    print("\n[Step 3/4] Pushing code to GitHub...")
    run_command("git config --global --add safe.directory '*'")
    run_command(f"git config --global --add safe.directory '{cwd}'")
    run_command("git init")
    run_command("git config core.fileMode false")
    run_command("git config user.email 'dev@pranklock.app'")
    run_command("git config user.name 'PrankLock Dev'")
    run_command("git branch -M main")

    remote_url = f"https://{username}:{token}@github.com/{username}/{FIXED_REPO_NAME}.git"
    run_command("git remote remove origin 2>/dev/null")
    run_command(f"git remote add origin {remote_url}")

    runs_url = f"https://api.github.com/repos/{username}/{FIXED_REPO_NAME}/actions/runs?per_page=10"
    initial_runs_data = api_request(runs_url, token)
    latest_old_run_id = None
    if initial_runs_data and initial_runs_data.get("workflow_runs"):
        latest_old_run_id = initial_runs_data["workflow_runs"][0]["id"]

    run_command("git add -A")
    status_res = run_command("git status --porcelain")
    status_output = status_res.stdout.strip()

    if status_output:
        print("\n-> Files being pushed:")
        for line in status_output.splitlines():
            code = line[:2].strip()
            file_name = line[3:].strip()
            tag = "[New]" if any(c in code for c in ["A","?"]) else "[Modified]" if "M" in code else "[Deleted]" if "D" in code else "[Updated]"
            print(f"   {tag:<12} -> {file_name}")
        commit_msg = f"PrankLock Update: {time.strftime('%Y-%m-%d %H:%M:%S')}"
        run_command(f"git commit -m '{commit_msg}'")
    else:
        print("-> No changes detected, forcing sync...")
        ts = time.strftime("%Y-%m-%d %H:%M:%S")
        run_command("git commit -m 'Sync: " + ts + "' --allow-empty")

    push_res = run_command("git push -u origin main --force")
    if push_res.returncode != 0:
        print(f"[ERROR] Push Failed:\n{push_res.stderr}")
        sys.exit(1)
    print("-> Push successful!")

    print("\n[Step 4/4] Monitoring GitHub Actions build...")
    actions_url = f"https://github.com/{username}/{FIXED_REPO_NAME}/actions"
    print("-> Waiting for build to start...")
    run_id = None
    for _ in range(25):
        time.sleep(3)
        runs_data = api_request(runs_url, token)
        if runs_data and runs_data.get("workflow_runs"):
            current_run = runs_data["workflow_runs"][0]
            if current_run["id"] != latest_old_run_id or current_run.get("status") in ["queued", "in_progress"]:
                run_id = current_run["id"]
                break

    if not run_id:
        print(f"\nBuild queued. Check manually:\n{actions_url}")
        return

    print(f"-> Building APK (Run #{run_id})... please wait")
    run_status_url = f"https://api.github.com/repos/{username}/{FIXED_REPO_NAME}/actions/runs/{run_id}"
    while True:
        status_data = api_request(run_status_url, token)
        if not status_data:
            time.sleep(5)
            continue
        status = status_data.get("status")
        conclusion = status_data.get("conclusion")
        if status == "completed":
            if conclusion == "success":
                print("\n" + "=" * 50)
                print("BUILD SUCCESSFUL!")
                print(f"Download APK:\n{actions_url}/runs/{run_id}")
                print("=" * 50)
            else:
                print(f"\n[FAILED] Build status: {conclusion}")
                print(f"Logs: {actions_url}/runs/{run_id}")
            break
        else:
            sys.stdout.write(".")
            sys.stdout.flush()
            time.sleep(8)

if __name__ == "__main__":
    main()
