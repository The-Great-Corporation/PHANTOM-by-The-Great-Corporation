import json
import pathlib

skins = list(pathlib.Path("android/app/src/main/assets/skins").glob("*.json"))
print(f"{len(skins)} skins trouves")

required = {"skin_id", "display_name", "schema_version", "buttons", "dpad_color", "bumper_color", "center_color"}
all_ok = True
for f in sorted(skins):
    d = json.loads(f.read_text(encoding="utf-8"))
    missing = required - d.keys()
    btns = set(d.get("buttons", {}).keys())
    ok = not missing and btns == {"a", "b", "x", "y"}
    if not ok:
        all_ok = False
    status = "OK" if ok else "FAIL"
    print(f"  {status} {f.name:22s} | v{d.get('schema_version')} | buttons={sorted(btns)} | missing={missing}")

print("RESULTAT:", "PASS" if all_ok else "FAIL")
