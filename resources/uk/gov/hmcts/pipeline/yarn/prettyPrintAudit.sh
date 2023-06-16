#!/bin/bash
filename="$1"

while IFS= read -r line; do
    echo "$line" | jq -r '
        .module_name as $moduleName |
        .id as $id |
        .title as $issue |
        .url as $url |
        .severity as $severity |
        .vulnerable_versions as $vulnVers |
        .patched_versions as $patchVers |
        .recommendation as $rec |
        .findings[] |
        "├─ " + $moduleName + ": " + .version,
        "│  ├─ ID: " + ($id|tostring),
        "│  ├─ Issue: " + $issue,
        "│  ├─ URL: " + $url,
        "│  ├─ Severity: " + $severity,
        "│  ├─ Vulnerable Versions: " + $vulnVers,
        "│  ├─ Patched Versions: " + $patchVers,
        "│  ├─ Via: " + (.paths | join(", ")),
        "│  └─ Recommendation: " + $rec,
        ""'
done < "$filename"

