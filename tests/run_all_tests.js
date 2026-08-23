/**
 * Master Test Runner: TV Dinner Multimedia Application
 * Discovers and runs test tiers with detailed reporting and exit codes.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';
import { clearSuiteRegistry, runAllRegisteredSuites } from './harness.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ANSI = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  green: '\x1b[32m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
  gray: '\x1b[90m'
};

const SUITES = [
  { file: 'test_universal_navigation.js', name: 'Universal Navigation & Spatial Engine', required: true },
  { file: 'tier1_feature_coverage.js', name: 'Tier 1: Feature Coverage (F01-F22)', required: true },
  { file: 'tier2_boundary_corner.js', name: 'Tier 2: Boundary & Corner Cases', required: true },
  { file: 'tier3_pairwise_combinations.js', name: 'Tier 3: Pairwise Combinations', required: true },
  { file: 'tier4_application_scenarios.js', name: 'Tier 4: Application Scenarios', required: true },
  { file: 'tier5_adversarial_stress.js', name: 'Tier 5: Adversarial Stress & Hardening', required: true }
];

async function run() {
  console.log(`\n${ANSI.bold}${ANSI.cyan}======================================================${ANSI.reset}`);
  console.log(`${ANSI.bold}${ANSI.cyan}   TV DINNER AUTOMATED E2E & SPECIFICATION TEST SUITE ${ANSI.reset}`);
  console.log(`${ANSI.bold}${ANSI.cyan}======================================================${ANSI.reset}\n`);

  const summary = {
    totalSuites: 0,
    executedSuites: 0,
    passedSuites: 0,
    failedSuites: 0,
    totalTests: 0,
    passedTests: 0,
    failedTests: 0,
    tierBreakdown: [],
    startTime: Date.now()
  };

  const args = process.argv.slice(2);
  const filterSuite = args.find(a => !a.startsWith('-'));

  for (const suiteDef of SUITES) {
    if (filterSuite && !suiteDef.file.includes(filterSuite) && !suiteDef.name.toLowerCase().includes(filterSuite.toLowerCase())) {
      continue;
    }

    summary.totalSuites++;
    const suitePath = path.join(__dirname, suiteDef.file);
    if (!fs.existsSync(suitePath)) {
      if (suiteDef.required) {
        console.log(`${ANSI.red}✖ Missing required suite file:${ANSI.reset} ${suiteDef.file}`);
        summary.failedSuites++;
      } else {
        console.log(`${ANSI.dim}○ Suite not yet implemented (skipped):${ANSI.reset} ${suiteDef.file}`);
      }
      continue;
    }

    console.log(`${ANSI.bold}${ANSI.blue}▶ Executing: ${suiteDef.name}${ANSI.reset} (${suiteDef.file})`);
    clearSuiteRegistry();

    try {
      // Import the test file to register its suites
      const moduleUrl = `${pathToFileURL(suitePath).href}?t=${Date.now()}`;
      await import(moduleUrl);

      const suiteStart = Date.now();
      const suiteResults = await runAllRegisteredSuites();
      const suiteDuration = Date.now() - suiteStart;

      summary.executedSuites++;
      summary.totalTests += suiteResults.total;
      summary.passedTests += suiteResults.passed;
      summary.failedTests += suiteResults.failed;

      const passed = suiteResults.failed === 0 && suiteResults.total > 0;
      if (passed) {
        summary.passedSuites++;
        console.log(`  ${ANSI.green}✔ ${suiteDef.name} PASSED${ANSI.reset} (${suiteResults.passed}/${suiteResults.total} tests in ${suiteDuration}ms)\n`);
      } else {
        summary.failedSuites++;
        console.log(`  ${ANSI.red}✖ ${suiteDef.name} FAILED${ANSI.reset} (${suiteResults.failed} failures out of ${suiteResults.total} tests)\n`);
      }

      // Print per-feature group breakdown if available
      for (const group of suiteResults.suites) {
        const grpPass = group.failed === 0;
        const mark = grpPass ? `${ANSI.green}✓${ANSI.reset}` : `${ANSI.red}✗${ANSI.reset}`;
        console.log(`    ${mark} ${group.name.padEnd(50)} [${group.passed}/${group.total}]`);
        if (!grpPass) {
          for (const t of group.tests) {
            if (t.status === 'failed') {
              console.log(`       ${ANSI.red}↳ Failed: ${t.name}${ANSI.reset}`);
              if (t.error) {
                console.log(`         ${ANSI.dim}${t.error.stack || t.error.message}${ANSI.reset}`);
              }
            }
          }
        }
      }
      console.log('');

      summary.tierBreakdown.push({
        tier: suiteDef.name,
        total: suiteResults.total,
        passed: suiteResults.passed,
        failed: suiteResults.failed,
        durationMs: suiteDuration
      });

    } catch (err) {
      summary.failedSuites++;
      console.log(`  ${ANSI.red}✖ Suite execution error in ${suiteDef.file}:${ANSI.reset}`, err);
    }
  }

  const totalDuration = Date.now() - summary.startTime;

  // Render Final Report Summary Table
  console.log(`${ANSI.bold}──────────────────────────────────────────────────────${ANSI.reset}`);
  console.log(`${ANSI.bold}TEST EXECUTION SUMMARY${ANSI.reset}`);
  console.log(`${ANSI.bold}──────────────────────────────────────────────────────${ANSI.reset}`);
  console.log(`Total Test Suites   : ${summary.executedSuites}/${summary.totalSuites}`);
  console.log(`Total Test Cases    : ${summary.totalTests}`);
  console.log(`Passing Tests       : ${ANSI.green}${summary.passedTests}${ANSI.reset}`);
  console.log(`Failing Tests       : ${summary.failedTests > 0 ? ANSI.red : ANSI.green}${summary.failedTests}${ANSI.reset}`);
  console.log(`Total Execution Time: ${totalDuration}ms`);
  console.log(`${ANSI.bold}──────────────────────────────────────────────────────${ANSI.reset}`);

  for (const tb of summary.tierBreakdown) {
    const statusText = tb.failed === 0 ? `${ANSI.green}PASS${ANSI.reset}` : `${ANSI.red}FAIL${ANSI.reset}`;
    console.log(` ${statusText} | ${tb.tier.padEnd(42)} | ${String(tb.passed).padStart(3)}/${String(tb.total).padEnd(3)} (${tb.durationMs}ms)`);
  }
  console.log(`${ANSI.bold}──────────────────────────────────────────────────────${ANSI.reset}\n`);

  if (summary.failedTests > 0 || summary.failedSuites > 0 || summary.totalTests === 0) {
    console.log(`${ANSI.bold}${ANSI.red}OVERALL STATUS: FAILED ✖${ANSI.reset}\n`);
    process.exit(1);
  } else {
    console.log(`${ANSI.bold}${ANSI.green}OVERALL STATUS: ALL TESTS PASSED ✔${ANSI.reset}\n`);
    process.exit(0);
  }
}

run().catch((err) => {
  console.error('Fatal test runner error:', err);
  process.exit(1);
});
