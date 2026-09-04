# E2E Test Infra: TV Dinner Multimedia Application

## Test Philosophy
- **Opaque-box & Requirement-Driven**: Tests are designed directly against requirements in `ORIGINAL_REQUEST.md` and feature definitions in `PROJECT.md`.
- **Zero Heavy Runtime Dependency**: Uses a pure Node.js modular test runner (`tests/run_all_tests.js`) capable of verifying unit, contract, protocol, integration, and E2E simulation flows across web and native bridge layers.
- **Systematic Multi-Tier Testing**: Category-Partition, Boundary Value Analysis (BVA), Pairwise Combinatorial Testing, Realistic Application Workflows, and Adversarial White-Box Stress Hardening.

## Feature Inventory (22 Features)
| # | Feature | Source (Requirement) | Tier 1 (Min 5) | Tier 2 (Min 5) | Tier 3 (Pairwise) | Tier 4 (Scenario) | Tier 5 (Adversarial) |
|---|---------|----------------------|:--------------:|:--------------:|:-----------------:|:-----------------:|:--------------------:|
| F01 | Responsive Navigation Layout | ORIGINAL_REQUEST §R1, PROJECT F1 | 5 | 5 | ✓ | ✓ | ✓ |
| F02 | Clean Module State Teardown | ORIGINAL_REQUEST §R1, PROJECT F2 | 5 | 5 | ✓ | ✓ | ✓ |
| F03 | Instant Activation Unlocking | ORIGINAL_REQUEST §R1, PROJECT F3 | 5 | 5 | ✓ | ✓ | ✓ |
| F04 | Channel Grid Rendering Optimization | ORIGINAL_REQUEST §R2, PROJECT F4 | 5 | 5 | ✓ | ✓ | ✓ |
| F05 | Scoped Error Fixes & Modal Stability | ORIGINAL_REQUEST §R1, PROJECT F5 | 5 | 5 | ✓ | ✓ | ✓ |
| F06 | Instant Live TV HLS Playback | ORIGINAL_REQUEST §R2, PROJECT F6 | 5 | 5 | ✓ | ✓ | ✓ |
| F07 | Native Proxy Live Stream Rewriting | ORIGINAL_REQUEST §R2/R5, PROJECT F7 | 5 | 5 | ✓ | ✓ | ✓ |
| F08 | Real-Time EPG Program Guide | ORIGINAL_REQUEST §R2, PROJECT F8 | 5 | 5 | ✓ | ✓ | ✓ |
| F09 | Fullscreen Live Player Controls | ORIGINAL_REQUEST §R2, PROJECT F9 | 5 | 5 | ✓ | ✓ | ✓ |
| F10 | Xtream Movies & Series VOD Streaming | ORIGINAL_REQUEST §R3, PROJECT F10 | 5 | 5 | ✓ | ✓ | ✓ |
| F11 | Dynamic Series Container Extension | ORIGINAL_REQUEST §R3, PROJECT F11 | 5 | 5 | ✓ | ✓ | ✓ |
| F12 | Rich VOD Details & Episode Picker | ORIGINAL_REQUEST §R3, PROJECT F12 | 5 | 5 | ✓ | ✓ | ✓ |
| F13 | VOD Playback Resume Tracking | ORIGINAL_REQUEST §R3, PROJECT F13 | 5 | 5 | ✓ | ✓ | ✓ |
| F14 | Audio Podcasts & Dock Player | ORIGINAL_REQUEST §R4, PROJECT F14 | 5 | 5 | ✓ | ✓ | ✓ |
| F15 | Podcast RSS Feed & YouTube Ingestion | ORIGINAL_REQUEST §R4, PROJECT F15 | 5 | 5 | ✓ | ✓ | ✓ |
| F16 | Podcast Subscription & Episode Tracking | ORIGINAL_REQUEST §R4, PROJECT F16 | 5 | 5 | ✓ | ✓ | ✓ |
| F17 | Video Podcast Playback & Fullscreen | ORIGINAL_REQUEST §R4, PROJECT F17 | 5 | 5 | ✓ | ✓ | ✓ |
| F18 | Permissive SSL Trust in Native Proxy | ORIGINAL_REQUEST §R5, PROJECT F18 | 5 | 5 | ✓ | ✓ | ✓ |
| F19 | Android Native Unit Test Stabilization | ORIGINAL_REQUEST §R5, PROJECT F19 | 5 | 5 | ✓ | ✓ | ✓ |
| F20 | Android Release Packaging & Proguard | ORIGINAL_REQUEST §R5, PROJECT F20 | 5 | 5 | ✓ | ✓ | ✓ |
| F21 | Comprehensive E2E Test Suite | Criteria, PROJECT F21 | 5 | 5 | ✓ | ✓ | ✓ |
| F22 | Adversarial Hardening & Smoke Verification | Criteria, PROJECT F22 | 5 | 5 | ✓ | ✓ | ✓ |

## Test Architecture & Directory Layout
```
tests/
├── run_all_tests.js            # Main test runner with reporter and exit code handling
├── harness.js                  # Lightweight test assertion library & mock DOM/environment
├── tier1_feature_coverage.js   # 110 Tier 1 tests (5 tests x 22 features)
├── tier2_boundary_corner.js    # 110 Tier 2 tests (5 tests x 22 features)
├── tier3_pairwise_combinations.js # 22 Tier 3 pairwise integration tests
├── tier4_application_scenarios.js # 11 Tier 4 end-to-end user workflow scenarios
└── tier5_adversarial_stress.js # 84 Tier 5 adversarial stress & hardening tests
```

## Test Runner Invocation
- Command: `node tests/run_all_tests.js`
- Exit Code: 0 on all tests passing, non-zero on any failure.
- Tier-specific execution:
  - `node tests/run_all_tests.js tier1`
  - `node tests/run_all_tests.js tier2`
  - `node tests/run_all_tests.js tier3`
  - `node tests/run_all_tests.js tier4`
  - `node tests/run_all_tests.js tier5`

## Coverage Thresholds
- **Tier 1 (Feature Coverage)**: >=110 test cases (5 per feature).
- **Tier 2 (Boundary & Corner Cases)**: >=110 test cases (5 per feature).
- **Tier 3 (Cross-Feature Combinations)**: >=22 test cases (pairwise interactions).
- **Tier 4 (Real-World Application Scenarios)**: >=11 scenario workflows.
- **Tier 5 (Adversarial Stress & Hardening)**: >=84 test cases (white-box stress, race conditions, malformed manifests/EPG/RSS, native bridge contracts).
- **Total Minimum Test Count**: 337 test cases.
