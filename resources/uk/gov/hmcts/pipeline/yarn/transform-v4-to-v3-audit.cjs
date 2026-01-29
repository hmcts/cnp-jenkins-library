/* This script transforms Yarn v4 audit JSON output to Yarn v3 audit JSON output format
   Currently, within Yarn 4.x audit reports, there is some information missing such as CVSS scores
   This file queries the GitHub Advisory API (which is the same registry that the NPM Audit queries) to retrieve the missing information
   This is then converted to a standard report format that we store within our Cosmos DB for ingestion into the Grafana dashboard
*/
const fs = require("fs");

async function main() {
  const v3input = fs.readFileSync('/dev/stdin', "utf-8");

  const v3lines = v3input.split("\n").filter(line => line.length > 0);
  const v3json = v3lines.map(line => JSON.parse(line));

  const advisories = await Promise.all(v3json.map(getAdvisory));
  const yarnLock = fs.readFileSync('yarn.lock', 'utf8');
  const numDependencies = yarnLock.match(/resolution: "/g).length;
  const advisoriesById = advisories.reduce((acc, advisory) => {
    acc[advisory.id] = advisory;
    return acc;
  }, {});

  console.log(JSON.stringify({
    actions: [],
    advisories: advisoriesById,
    metadata: {
      dependencies: numDependencies,
      devDependencies: 0,
      optionalDependencies: 0,
      totalDependencies: numDependencies,
      vulnerabilities: {
        critical: advisories.filter(advisory => advisory.severity === 'critical').length,
        high: advisories.filter(advisory => advisory.severity === 'high').length,
        info: advisories.filter(advisory => advisory.severity === 'info').length,
        low: advisories.filter(advisory => advisory.severity === 'low').length,
        moderate: advisories.filter(advisory => advisory.severity === 'moderate').length
      }
    },
    muted: []
  }, null, 2));
}

async function getAdvisory(advisory) {
  const partialResult = {
    access: "public",
    created: null,
    cves: [],
    cvss: advisory.children.cvss ? [advisory.children.cvss] : [],
    cwe: [],
    deleted: null,
    findings: [{
      paths: advisory.children.Dependents,
      version: advisory.children["Tree Versions"][0]
    }],
    found_by: null,
    github_advisory_id: null,
    id: advisory.children.ID,
    metadata: null,
    module_name: advisory.value,
    npm_advisory_id: null,
    overview: advisory.children.Issue,
    patched_versions: null,
    recommendation: null,
    references: "",
    reported_by: null,
    severity: advisory.children.Severity,
    title: advisory.children.Issue,
    updated: null,
    url: null,
    vulnerable_versions: advisory.children["Vulnerable Versions"]
  }

  const githubJson = !advisory.children.URL
    ? {}
    : await getGitHubAdvisory(advisory.children.URL);

  return { ...partialResult, ...githubJson };
}

async function getGitHubAdvisory(htmlUrl) {
  const url = htmlUrl.replace('github.com', 'api.github.com');
  const request = await fetch(url, {
    headers: {
      "Authorization": "Bearer " + process.env.BEARER_TOKEN,
      "X-GitHub-Api-Version": "2022-11-28"
    }
  });

  if (!request.ok) {
    return {
      title: `Could not fetch GitHub Advisory; ResultCode: ${request.status}, Body: ${await request.text()}`
    }
  }

  const githubJson = await request.json();

  return {
    created: githubJson.published_at,
    cves: [githubJson.cve_id],
    cvss: githubJson.cvss,
    cwe: githubJson.cwes?.map(cwe => cwe.cwe_id),
    deleted: githubJson.withdrawn_at,
    github_advisory_id: githubJson.ghsa_id,
    overview: githubJson.description,
    patched_versions: githubJson.vulnerabilities?.[0].first_patched_version,
    recommendation: `Upgrade to ${githubJson.vulnerabilities?.[0].first_patched_version}`,
    title: githubJson.summary,
    updated: githubJson.updated_at,
    url: githubJson.html_url,
    vulnerable_versions: githubJson.vulnerabilities?.[0].vulnerable_version_range
  }
}

main().catch(console.log);
