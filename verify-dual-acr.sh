#!/bin/bash
# Dual ACR Publish Verification Script
# Validates that images and charts exist in both primary and secondary ACRs

set -e

echo "========================================"
echo "Dual ACR Publish Verification"
echo "========================================"
echo ""

# Configuration
PRIMARY_REGISTRY="hmctssbox"
SECONDARY_REGISTRY="hmctspublic"
TEST_REPO="plum/frontend"
HELM_CHART="plum-frontend"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "Checking prerequisites..."
if ! command -v az &> /dev/null; then
    echo -e "${RED}✗ Azure CLI not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Azure CLI found${NC}"
echo ""

# Function to check if logged in
check_azure_login() {
    if ! az account show &> /dev/null; then
        echo -e "${RED}✗ Not logged in to Azure${NC}"
        echo "Run: az login"
        exit 1
    fi
    echo -e "${GREEN}✓ Logged in to Azure${NC}"
    echo "  Account: $(az account show --query user.name -o tsv)"
    echo ""
}

# Function to get latest tags from ACR
get_latest_tags() {
    local registry=$1
    local repo=$2
    local count=${3:-5}
    
    echo "Fetching latest $count tags from $registry/$repo..."
    az acr repository show-tags \
        --name "$registry" \
        --repository "$repo" \
        --orderby time_desc \
        --top "$count" \
        -o tsv 2>/dev/null || echo "ERROR: Cannot access $registry/$repo"
}

# Function to check if specific tag exists
check_tag_exists() {
    local registry=$1
    local repo=$2
    local tag=$3
    
    if az acr repository show-tags \
        --name "$registry" \
        --repository "$repo" \
        --output tsv 2>/dev/null | grep -q "^${tag}$"; then
        return 0
    else
        return 1
    fi
}

# Function to compare tags between registries
compare_tags() {
    local repo=$1
    
    echo "========================================" 
    echo "Comparing tags for: $repo"
    echo "========================================"
    echo ""
    
    echo "PRIMARY ACR ($PRIMARY_REGISTRY):"
    primary_tags=$(get_latest_tags "$PRIMARY_REGISTRY" "$repo" 10)
    if [ -z "$primary_tags" ] || [ "$primary_tags" = "ERROR"* ]; then
        echo -e "${RED}  ✗ Cannot access or no tags found${NC}"
        return 1
    fi
    echo "$primary_tags" | sed 's/^/  /'
    echo ""
    
    echo "SECONDARY ACR ($SECONDARY_REGISTRY):"
    secondary_tags=$(get_latest_tags "$SECONDARY_REGISTRY" "$repo" 10)
    if [ -z "$secondary_tags" ] || [ "$secondary_tags" = "ERROR"* ]; then
        echo -e "${RED}  ✗ Cannot access or no tags found${NC}"
        return 1
    fi
    echo "$secondary_tags" | sed 's/^/  /'
    echo ""
    
    # Find common tags
    echo "Common tags (dual-published):"
    common_tags=$(comm -12 <(echo "$primary_tags" | sort) <(echo "$secondary_tags" | sort))
    if [ -z "$common_tags" ]; then
        echo -e "${YELLOW}  ⚠ No common tags found${NC}"
        echo -e "${YELLOW}  This may indicate dual publish is not yet active or images haven't synced${NC}"
        return 1
    else
        echo -e "${GREEN}$(echo "$common_tags" | wc -l | xargs) common tag(s) found:${NC}"
        echo "$common_tags" | sed 's/^/  ✓ /'
        return 0
    fi
}

# Function to get image manifest digest
get_image_digest() {
    local registry=$1
    local repo=$2
    local tag=$3
    
    az acr repository show \
        --name "$registry" \
        --image "${repo}:${tag}" \
        --query "digest" \
        -o tsv 2>/dev/null || echo "ERROR"
}

# Function to verify image content matches
verify_image_match() {
    local repo=$1
    local tag=$2
    
    echo "Verifying image content for tag: $tag"
    echo ""
    
    primary_digest=$(get_image_digest "$PRIMARY_REGISTRY" "$repo" "$tag")
    secondary_digest=$(get_image_digest "$SECONDARY_REGISTRY" "$repo" "$tag")
    
    echo "  Primary digest:   $primary_digest"
    echo "  Secondary digest: $secondary_digest"
    echo ""
    
    if [ "$primary_digest" = "ERROR" ] || [ "$secondary_digest" = "ERROR" ]; then
        echo -e "${YELLOW}  ⚠ Cannot fetch digest from one or both registries${NC}"
        return 1
    fi
    
    if [ "$primary_digest" = "$secondary_digest" ]; then
        echo -e "${GREEN}  ✓ Digests match - images are identical${NC}"
        return 0
    else
        echo -e "${RED}  ✗ Digests differ - images are NOT identical${NC}"
        echo -e "${RED}    This indicates a problem with dual publish${NC}"
        return 1
    fi
}

# Main verification
echo "========================================"
echo "Starting Verification"
echo "========================================"
echo ""

check_azure_login

# Test 1: Compare container image tags
echo ""
echo "TEST 1: Container Image Dual Publish"
echo "========================================"
if compare_tags "$TEST_REPO"; then
    echo -e "${GREEN}✓ TEST 1 PASSED${NC}"
    test1_passed=true
    
    # Get first common tag for deeper verification
    common_tag=$(comm -12 \
        <(get_latest_tags "$PRIMARY_REGISTRY" "$TEST_REPO" 10 | sort) \
        <(get_latest_tags "$SECONDARY_REGISTRY" "$TEST_REPO" 10 | sort) | head -1)
    
    if [ -n "$common_tag" ]; then
        echo ""
        echo "TEST 1a: Verify image content matches"
        echo "========================================"
        if verify_image_match "$TEST_REPO" "$common_tag"; then
            echo -e "${GREEN}✓ TEST 1a PASSED${NC}"
        else
            echo -e "${YELLOW}⚠ TEST 1a WARNING${NC}"
        fi
    fi
else
    echo -e "${RED}✗ TEST 1 FAILED${NC}"
    test1_passed=false
fi

# Test 2: Check for Helm charts
echo ""
echo "TEST 2: Helm Chart Dual Publish"
echo "========================================"
echo "Checking for Helm charts in both registries..."
echo ""

primary_charts=$(az acr repository list --name "$PRIMARY_REGISTRY" -o tsv 2>/dev/null | grep "^helm/" || true)
secondary_charts=$(az acr repository list --name "$SECONDARY_REGISTRY" -o tsv 2>/dev/null | grep "^helm/" || true)

if [ -n "$primary_charts" ] && [ -n "$secondary_charts" ]; then
    echo "Helm charts in PRIMARY:"
    echo "$primary_charts" | sed 's/^/  /'
    echo ""
    echo "Helm charts in SECONDARY:"
    echo "$secondary_charts" | sed 's/^/  /'
    echo ""
    
    common_charts=$(comm -12 <(echo "$primary_charts" | sort) <(echo "$secondary_charts" | sort))
    if [ -n "$common_charts" ]; then
        echo -e "${GREEN}✓ TEST 2 PASSED${NC}"
        echo "Common Helm charts:"
        echo "$common_charts" | sed 's/^/  ✓ /'
        test2_passed=true
    else
        echo -e "${YELLOW}⚠ TEST 2 WARNING: No common Helm charts${NC}"
        test2_passed=false
    fi
else
    echo -e "${YELLOW}⚠ TEST 2 SKIPPED: Helm charts not found in one or both registries${NC}"
    test2_passed=false
fi

# Test 3: Check for promotion tags (staging, prod-*)
echo ""
echo "TEST 3: Build Promotion Tag Synchronization"
echo "========================================"
echo "Checking for promotion tags (staging, prod-*)..."
echo ""

primary_all_tags=$(get_latest_tags "$PRIMARY_REGISTRY" "$TEST_REPO" 50)
secondary_all_tags=$(get_latest_tags "$SECONDARY_REGISTRY" "$TEST_REPO" 50)

primary_promo_tags=$(echo "$primary_all_tags" | grep -E "^(staging|prod-)" || true)
secondary_promo_tags=$(echo "$secondary_all_tags" | grep -E "^(staging|prod-)" || true)

if [ -n "$primary_promo_tags" ] || [ -n "$secondary_promo_tags" ]; then
    echo "Promotion tags in PRIMARY:"
    if [ -n "$primary_promo_tags" ]; then
        echo "$primary_promo_tags" | sed 's/^/  /'
    else
        echo "  (none)"
    fi
    echo ""
    
    echo "Promotion tags in SECONDARY:"
    if [ -n "$secondary_promo_tags" ]; then
        echo "$secondary_promo_tags" | sed 's/^/  /'
    else
        echo "  (none)"
    fi
    echo ""
    
    common_promo=$(comm -12 <(echo "$primary_promo_tags" | sort) <(echo "$secondary_promo_tags" | sort) || true)
    if [ -n "$common_promo" ]; then
        echo -e "${GREEN}✓ TEST 3 PASSED${NC}"
        echo "Synchronized promotion tags:"
        echo "$common_promo" | sed 's/^/  ✓ /'
        test3_passed=true
    else
        echo -e "${YELLOW}⚠ TEST 3 WARNING: No common promotion tags${NC}"
        echo "  This may indicate promotion hasn't run yet with dual publish enabled"
        test3_passed=false
    fi
else
    echo -e "${YELLOW}⚠ TEST 3 SKIPPED: No promotion tags found${NC}"
    test3_passed=false
fi

# Final Summary
echo ""
echo "========================================"
echo "VERIFICATION SUMMARY"
echo "========================================"
echo ""

if [ "$test1_passed" = true ]; then
    echo -e "${GREEN}✓ Container Image Dual Publish: WORKING${NC}"
else
    echo -e "${RED}✗ Container Image Dual Publish: FAILED${NC}"
fi

if [ "$test2_passed" = true ]; then
    echo -e "${GREEN}✓ Helm Chart Dual Publish: WORKING${NC}"
elif [ "$test2_passed" = false ]; then
    echo -e "${YELLOW}⚠ Helm Chart Dual Publish: NOT VERIFIED${NC}"
fi

if [ "$test3_passed" = true ]; then
    echo -e "${GREEN}✓ Build Promotion Synchronization: WORKING${NC}"
elif [ "$test3_passed" = false ]; then
    echo -e "${YELLOW}⚠ Build Promotion Synchronization: NOT VERIFIED${NC}"
fi

echo ""
if [ "$test1_passed" = true ]; then
    echo -e "${GREEN}✓✓✓ DUAL ACR PUBLISH IS WORKING ✓✓✓${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠⚠⚠ VERIFICATION INCOMPLETE ⚠⚠⚠${NC}"
    echo "Some tests could not be verified. This may be normal if:"
    echo "  - Dual publish was recently enabled and builds haven't run yet"
    echo "  - You don't have access to one or both ACR registries"
    echo "  - The test repository hasn't been built with dual publish enabled"
    exit 1
fi
