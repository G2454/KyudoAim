#!/usr/bin/env python3
"""Prints line coverage per package from the Kover (JaCoCo-format) XML report: to the log,
to the GitHub job summary, and as a notice annotation readable through the public API."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

reports = glob.glob("app/build/reports/kover/**/*.xml", recursive=True)
if not reports:
    print("No Kover XML report found.")
    sys.exit(0)

root = ET.parse(reports[0]).getroot()


def line_pct(node):
    for c in node.findall("counter"):
        if c.get("type") == "LINE":
            missed, covered = int(c.get("missed")), int(c.get("covered"))
            total = missed + covered
            return (100.0 * covered / total if total else 100.0), covered, total
    return None


rows = []
for pkg in root.findall("package"):
    r = line_pct(pkg)
    if r:
        rows.append((pkg.get("name").replace("/", "."), *r))
total = line_pct(root)

lines = ["| Package | Line coverage | Lines |", "|---|---:|---:|"]
for name, pct, cov, tot in sorted(rows):
    lines.append(f"| `{name}` | {pct:.1f}% | {cov}/{tot} |")
if total:
    lines.append(f"| **Total** | **{total[0]:.1f}%** | {total[1]}/{total[2]} |")
table = "\n".join(lines)
print(table)

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write("## Test coverage (JVM tests)\n\n" + table + "\n")

if total:
    parts = ", ".join(f"{n.split('.')[-1]} {p:.0f}%" for n, p, _, _ in sorted(rows))
    print(f"::notice title=Coverage {total[0]:.1f}%::Line coverage {total[0]:.1f}% ({parts})")
