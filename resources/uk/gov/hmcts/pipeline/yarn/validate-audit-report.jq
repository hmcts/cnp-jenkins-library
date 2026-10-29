def text_ok: type == "string" and test("\\S");
def count_ok: type == "number" and isfinite and . >= 0 and floor == .;
def id_ok: text_ok or count_ok;
def severities: ["info", "low", "moderate", "high", "critical"];

try (
  length == 1 and (.[0] as $report |
    ($report | type == "object") and
    ($report.metadata | type == "object") and
    ($report.metadata.vulnerabilities | type == "object") and
    ($report.advisories | type == "object") and
    all(severities[]; . as $severity |
      ($report.metadata.vulnerabilities[$severity] | count_ok) and
      (($report.metadata.vulnerabilities[$severity] > 0) ==
        any($report.advisories[]; .severity == $severity))) and
    all($report.advisories | to_entries[];
      .key == (.value.id | tostring) and (.value |
        type == "object" and
        (.id | id_ok) and
        (.severity as $severity | any(severities[]; . == $severity)) and
        (.module_name | text_ok) and (.title | text_ok) and (.vulnerable_versions | text_ok) and
        (.findings | type == "array" and length > 0 and all(.[];
          type == "object" and (.version | text_ok) and
          (.paths | type == "array" and all(.[]; text_ok)))))))
) catch false
