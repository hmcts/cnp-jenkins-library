import json
from dataclasses import dataclass, asdict
import subprocess
import os
from typing import List

@dataclass
class Vulnerability:
  name: str
  id: st
  severity: str
  vulnerable_versions: str
  issue: str
  url: str
  tree_versions: List[str]
  dependents: List[str]

  def to_json(self):
    return asdict(self)

  def format_vulnerability(self) -> str:
    return (
      f"├─ {self.name}\n"
      f"│  ├─ ID: {self.id if self.id is not None else 'N/A'}\n"
      f"│  ├─ URL: {self.url if self.url is not None else 'N/A'}\n"
      f"│  ├─ Issue: {self.issue if self.issue is not None else 'N/A'}\n"
      f"│  ├─ Severity: {self.severity if self.severity is not None else 'N/A'}\n"
      f"│  ├─ Vulnerable Versions: {self.vulnerable_versions if self.vulnerable_versions is not None else 'N/A'}\n"
      f"│  ├─ Tree Versions: {', '.join(self.tree_versions) if self.tree_versions else 'N/A'}\n"
      f"│  └─ Dependents: {', '.join(self.dependents) if self.dependents else 'N/A'}"
    )

  def __eq__(self, other):
    if isinstance(other, Vulnerability):
      return self.id == other.id
      # Compare based on id only - allows us to handle situations
      # where the other text of the vulnerability changes over time
    return False

def run_audit_command():
  command = ['yarn', 'npm', 'audit', '--recursive',  '--environment', 'production', '--json']
  result = subprocess.run(command, capture_output=True, text=True)
  # if any errors, print them and exit
  if result.stderr != '':
    print("Error running command `yarn npm audit --recursive --environment production --json` - Please raise a request in #platops-help. Error: ", repr(result.stderr))
    exit(1)
  else:
    return result.stdout, result.returncode

def validate_audit_response(response, exit_code):
  if exit_code == 0:
    print("No vulnerabilities found, nice work!")
    exit(0)
  list_of_issues = split_and_strip_json_blocks(response)
  print("Found", len(list_of_issues), "vulnerabilities")
  if check_json_keys(list_of_issues):
    vulnerabilities = build_list_of_issues(list_of_issues)
    return vulnerabilities
  else:
    print("Error parsing JSON returned from audit endpoint - please raise a request in #platops-help")
    exit(1)

def split_and_strip_json_blocks(json_block):
  stripped_response = json_block.strip()
  list_of_issues = stripped_response.split('\n')
  return list_of_issues

def check_yarn_audit_known_issues():
  if os.path.exists('yarn-audit-known-issues'):
    print("Found yarn-audit-known-issues file - checking for suppressed vulnerabilities")
    with open('yarn-audit-known-issues') as f:
      known_issues_file_content = f.read()
      known_issues_stripped = split_and_strip_json_blocks(known_issues_file_content)
      if check_json_keys(known_issues_stripped):
        known_issues_list = build_list_of_issues(known_issues_stripped)
        return known_issues_list
      else :
        print("Error parsing JSON in your yarn-audit-known-issues file - delete the file and use the following command:")
        print("`yarn npm audit --recursive --environment production --json > yarn-audit-known-issues`")
        exit(1)
  else:
    return []

def check_json_keys(json_blocks):
  try:
    for block in json_blocks:
      data = json.loads(block)
      # Check if both 'value' and 'children' keys are in the JSON block (yarn v4 schema)
      if "value" not in data or "children" not in data:
        return False
    return True
  except json.JSONDecodeError:
    return False

def combine_suppressions_and_vulnerabilities(suppressions, vulnerabilities):
  # comparison logic is based on the ID of the vulnerability - if the ID is the same, we assume it's the same
  # vulnerability
  unsuppressed_vulnerabilities = [item for item in vulnerabilities if item not in suppressions]
  unneeded_suppressions = [item for item in suppressions if item not in vulnerabilities]
  suppressed_active_vulnerabilities = [item for item in vulnerabilities if item in suppressions]
  return unsuppressed_vulnerabilities, unneeded_suppressions, suppressed_active_vulnerabilities


def build_list_of_issues(json_blocks):
  """
  Creates a list of Vulnerability objects from a list of JSON blocks.
  Each JSON block is parsed once and then used to construct a Vulnerability object.

  :param json_blocks: A list of JSON strings.
  :return: A list of Vulnerability objects.
  """
  vulnerabilities = []
  for block in json_blocks:
    parsed_data = json.loads(block)
    vuln = Vulnerability(
      name=parsed_data.get('value'),
      id=parsed_data.get('children', {}).get('ID'),
      issue=parsed_data.get('children', {}).get('Issue'),
      url=parsed_data.get('children', {}).get('URL'),
      severity=parsed_data.get('children', {}).get('Severity'),
      vulnerable_versions=parsed_data.get('children', {}).get('Vulnerable Versions'),
      tree_versions=parsed_data.get('children', {}).get('Tree Versions', []),
      dependents=parsed_data.get('children', {}).get('Dependents', [])
    )
    vulnerabilities.append(vuln)
  return vulnerabilities

def print_vulnerabilities(vulnerabilities):
  print("Found", len(vulnerabilities), "active vulnerabilities, please fix these before pushing again.")
  for vulnerability in vulnerabilities:
    print(vulnerability.format_vulnerability())

def print_suppressions(suppressions):
  print("Found", len(suppressions), "unnecessary suppression(s), please check these are still needed."
                                    "If not, please remove them from your yarn-audit-known-issues file")
  for suppression in suppressions:
    print(suppression.format_vulnerability())

def decide_what_to_print(unsuppressed_vulnerabilities, unneeded_suppressions):
  if len(unsuppressed_vulnerabilities) == 0 and len(unneeded_suppressions) == 0:
    print("All vulnerabilities are suppressed and there are no unneeded_suppressions - no action required, nice work!")
    return
  if len(unsuppressed_vulnerabilities) > 0:
    print_vulnerabilities(unsuppressed_vulnerabilities)
    return
  if len(unneeded_suppressions) > 0:
    print_suppressions(unneeded_suppressions)

def build_parent_json_for_cosmosDB(vulnerabilities, suppressions):
  if os.environ.get('ci') != 'True':
    print("Not running in CI, skipping parent JSON block")
  parent_block = {"suppressed_vulnerabilities": [s.to_json() for s in suppressions],
                  "unsuppressed_vulnerabilities": [v.to_json() for v in vulnerabilities]}
  with open("audit-v4-cosmosdb-output", "w") as file:
    file.write(json.dumps(parent_block, indent=2))

audit_output, return_code = run_audit_command()
vulnerabilities = validate_audit_response(audit_output, return_code)
suppressions = check_yarn_audit_known_issues()
unsuppressed_vulnerabilities, unneeded_suppressions, suppressed_active_vulnerabilities = combine_suppressions_and_vulnerabilities(suppressions, vulnerabilities)
decide_what_to_print(unsuppressed_vulnerabilities, unneeded_suppressions)
build_parent_json_for_cosmosDB(unsuppressed_vulnerabilities, suppressed_active_vulnerabilities)
