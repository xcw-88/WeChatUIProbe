"""Check the final APK manifest inputs and the read-only boundary in CI."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
android = "{http://schemas.android.com/apk/res/android}"
manifests = list((root / "app/build/intermediates/merged_manifests").glob("**/AndroidManifest.xml"))
assert manifests, "Build merged manifests before checking permissions"
for path in manifests:
    manifest = ET.parse(path).getroot()
    permissions = {entry.get(android + "name") for entry in manifest.findall("uses-permission")}
    assert permissions <= {
        "android.permission.SYSTEM_ALERT_WINDOW",
        # AndroidX prevents untrusted dynamic broadcast receivers; no user data capability.
        "dev.wechat.uiprobe.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    }, f"Unexpected permission in {path}: {permissions}"
    app = manifest.find("application")
    assert app is not None and app.get(android + "allowBackup") == "false"
    assert not manifest.findall("queries"), "Package visibility queries are not needed"
    assert not any("NotificationListener" in (service.get(android + "permission") or "")
                   for service in app.findall("service"))

source = root / "app/src/main/java/dev/wechat/uiprobe"
backup_rules = ET.parse(root / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
for section in ("cloud-backup", "device-transfer"):
    assert any(rule.get("domain") == "file" and rule.get("path") == "."
               for rule in backup_rules.find(section).findall("exclude")), f"Private exports can enter {section}"
for path in source.rglob("*.kt"):
    content = path.read_text(encoding="utf-8")
    assert not re.search(r"\b(?:performAction|dispatchGesture|takeScreenshot)\s*\(", content), path
    assert not re.search(r"\b(?:HttpURLConnection|OkHttpClient|WebView|Socket)\b", content), path
print(f"Privacy boundary passed for {len(manifests)} merged manifests and native Kotlin sources")
