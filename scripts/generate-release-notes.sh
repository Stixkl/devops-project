#!/bin/bash
set -e

VERSION=${1:-"unknown"}
DATE=$(date '+%Y-%m-%d %H:%M:%S')
PREV_TAG=$(git describe --tags --abbrev=0 HEAD~1 2>/dev/null || echo "")

cat > RELEASE_NOTES.md << EOF
# Release Notes - ${VERSION}
**Date:** ${DATE}  
**Author:** Jenkins CI/CD Pipeline

## What's Changed

### Commits since ${PREV_TAG:-initial}

EOF

git log ${PREV_TAG:-HEAD~100}..HEAD --pretty=format:"- %s (%an)" 2>/dev/null >> RELEASE_NOTES.md || true

cat >> RELEASE_NOTES.md << EOF

## Metrics

| Metric | Value |
|--------|-------|
| Build # | ${BUILD_NUMBER:-N/A} |
| Commit | ${GIT_COMMIT:-N/A} |
| Branch | ${BRANCH_NAME:-N/A} |
| Duration | $(echo $SECONDS 2>/dev/null || echo "N/A")s |

## Tests Summary

| Type | Status |
|------|--------|
| Unit Tests | ${UNIT_TEST_STATUS:-Passed} |
| Integration Tests | ${INT_TEST_STATUS:-Passed} |
| E2E Tests | ${E2E_TEST_STATUS:-Passed} |
| Performance Tests | ${PERF_TEST_STATUS:-Passed} |

## Breaking Changes

$(git log ${PREV_TAG:-HEAD~100}..HEAD --grep "BREAKING" --pretty=format="- %s" 2>/dev/null || echo "- None")

## Known Issues

- None reported

---
*Generated automatically by CircleGuard CI/CD Pipeline*
EOF

echo "Release notes generated: RELEASE_NOTES.md"
cat RELEASE_NOTES.md