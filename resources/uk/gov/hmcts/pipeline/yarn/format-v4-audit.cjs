const fs = require("fs");

async function main() {
  // Read the entire file as a JSON object
  const v3Data = JSON.parse(fs.readFileSync('/dev/stdin', "utf-8"));
  
  // Extract the advisories from the data
  const advisoryEntries = Object.entries(v3Data.advisories || {});
  
  const advisories = await Promise.all(advisoryEntries.map(([id, advisory]) => getAdvisory(id, advisory)));
  
  // Filter out null entries
  const validAdvisories = advisories.filter(advisory => advisory !== null);
  
  // Try to read yarn.lock if it exists, otherwise use a default value
  let numDependencies = 0;
  try {
    const yarnLock = fs.readFileSync('yarn.lock', 'utf8');
    numDependencies = (yarnLock.match(/resolution: "/g) || []).length;
  } catch (err) {
    console.warn("yarn.lock not found, using default dependency count");
    numDependencies = v3Data.metadata?.dependencies || 0;
  }
  
  // Convert to object with ID as key
  const advisoriesById = validAdvisories.reduce((acc, advisory) => {
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
        critical: validAdvisories.filter(advisory => advisory.severity === 'critical').length,
        high: validAdvisories.filter(advisory => advisory.severity === 'high').length,
        info: validAdvisories.filter(advisory => advisory.severity === 'info').length,
        low: validAdvisories.filter(advisory => advisory.severity === 'low').length,
        moderate: validAdvisories.filter(advisory => advisory.severity === 'moderate').length
      }
    },
    muted: []
  }, null, 2));
}

async function getAdvisory(id, advisory) {
  // If there are no findings, skip this advisory
  if (!advisory.findings || advisory.findings.length === 0) {
    return null;
  }
  
  // Extract paths from findings
  const paths = advisory.findings.flatMap(finding => finding.paths || []);
  
  // Get version from first finding
  const version = advisory.findings[0]?.version || null;
  
  const partialResult = {
    access: advisory.access || "public",
    created: advisory.created || null,
    cves: advisory.cves || [],
    cvss: advisory.cvss || null,
    cwe: advisory.cwe || [],
    deleted: advisory.deleted || null,
    findings: [{
      paths: paths,
      version: version
    }],
    found_by: advisory.found_by || null,
    github_advisory_id: advisory.github_advisory_id || null,
    id: id,
    metadata: advisory.metadata || null,
    module_name: advisory.module_name || null,
    npm_advisory_id: advisory.npm_advisory_id || null,
    overview: advisory.overview || advisory.title || "",
    patched_versions: advisory.patched_versions || null,
    recommendation: advisory.recommendation || null,
    references: advisory.references || "",
    reported_by: advisory.reported_by || null,
    severity: advisory.severity || "moderate",
    title: advisory.title || "",
    updated: advisory.updated || null,
    url: advisory.url || null,
    vulnerable_versions: advisory.vulnerable_versions || null
  };

  // If there's a GitHub advisory URL, try to fetch additional data
  if (advisory.url && advisory.url.includes('github.com/advisories')) {
    const githubJson = await getGitHubAdvisory(advisory.url);
    return { ...partialResult, ...githubJson };
  }

  return partialResult;
}

async function getGitHubAdvisory(htmlUrl) {
  const url = htmlUrl.replace('github.com', 'api.github.com');
  
  // Only try to fetch if we have a bearer token
  if (!process.env.BEARER_TOKEN) {
    console.warn("No BEARER_TOKEN environment variable set, skipping GitHub API fetch");
    return {};
  }
  
  try {
    const request = await fetch(url, {
      headers: {
        "Authorization": "Bearer " + process.env.BEARER_TOKEN,
        "X-GitHub-Api-Version": "2022-11-28"
      }
    });

    if (!request.ok) {
      console.warn(`Could not fetch GitHub Advisory from ${url}; Status: ${request.status}`);
      return {};
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
      patched_versions: githubJson.vulnerabilities?.[0]?.first_patched_version,
      recommendation: `Upgrade to ${githubJson.vulnerabilities?.[0]?.first_patched_version}`,
      title: githubJson.summary,
      updated: githubJson.updated_at,
      url: githubJson.html_url,
      vulnerable_versions: githubJson.vulnerabilities?.[0]?.vulnerable_version_range
    };
  } catch (error) {
    console.warn(`Error fetching GitHub advisory from ${url}:`, error.message);
    return {};
  }
}

main().catch(console.error);