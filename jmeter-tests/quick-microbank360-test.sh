#!/bin/bash

#===============================================================================
# MicroBank360 Quick Performance Test Script
#===============================================================================
# Description: Quick performance validation test for MicroBank360 Banking Microservices
# Created by:  PritiAryal
# Date:        2025-07-20 03:38:00 EST
# Version:     1.0
#
# Usage Examples:
#   ./quick-microbank360-test.sh                     # Default: 10 users, 120 seconds
#   ./quick-microbank360-test.sh 5 60                # 5 users, 60 seconds
#   ./quick-microbank360-test.sh 20 300 staging.com  # 20 users, 300 seconds, staging
#===============================================================================

set -euo pipefail

# Script Configuration
readonly SCRIPT_VERSION="1.0"
readonly SCRIPT_AUTHOR="PritiAryal"
readonly SCRIPT_DATE="2025-07-20 07:33:07 UTC"
readonly JMX_FILE="microbank360-performance-test.jmx"

# Default Configuration
THREADS=${1:-10}
DURATION=${2:-120}
BASE_URL=${3:-"localhost"}
PORT=${4:-8085}

# Derived Configuration
RAMP_UP=$((DURATION / 4))
if [ $RAMP_UP -lt 10 ]; then RAMP_UP=10; fi
if [ $RAMP_UP -gt 60 ]; then RAMP_UP=60; fi

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULT_DIR="quick-test-results-${TIMESTAMP}"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly GRAY='\033[0;37m'
readonly NC='\033[0m' # No Color

# Helper Functions
print_header() {
    echo -e "${CYAN}"
    echo " MicroBank360 Quick Performance Test"
    echo "======================================"
    echo -e "Created by: ${SCRIPT_AUTHOR}"
    echo -e "Version: ${SCRIPT_VERSION}"
    echo -e "Date: ${SCRIPT_DATE}"
    echo -e "Current Time: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo -e "${NC}"
}

print_config() {
    echo -e "${YELLOW} Test Configuration:${NC}"
    echo "====================="
    echo "Target: http://${BASE_URL}:${PORT}"
    echo "Virtual Users: ${THREADS}"
    echo "Test Duration: ${DURATION} seconds ($((DURATION/60)) minutes)"
    echo "Ramp-up Time: ${RAMP_UP} seconds"
    echo "Results Directory: ${RESULT_DIR}"
    echo ""
}

check_requirements() {
    echo -e "${YELLOW} Pre-flight System Checks:${NC}"
    echo "============================="

    # Check current directory
    echo "Current Directory: $(pwd)"

    # Check JMeter installation
    if command -v jmeter >/dev/null 2>&1; then
        JMETER_CMD="jmeter"
        echo -e "${GREEN} JMeter: Found in system PATH${NC}"
    elif [ -f "apache-jmeter-"*/bin/jmeter ]; then
        JMETER_CMD="$(find . -name "jmeter" -path "*/apache-jmeter-*/bin/jmeter" | head -1)"
        echo -e "${GREEN} JMeter: Found locally (${JMETER_CMD})${NC}"
    else
        echo -e "${RED} JMeter: Not found!${NC}"
        echo -e "${RED}Please ensure JMeter is installed or available in PATH${NC}"
        exit 1
    fi

    # Check test plan file
    if [ -f "${JMX_FILE}" ]; then
        echo -e "${GREEN} Test Plan: ${JMX_FILE} found${NC}"
    else
        echo -e "${RED} Test Plan: ${JMX_FILE} not found!${NC}"
        echo -e "${RED}Please ensure the JMX file exists in the current directory${NC}"
        exit 1
    fi

    # Check test data
    if [ -f "jmeter_testdata.csv" ]; then
        data_lines=$(wc -l < "jmeter_testdata.csv")
        data_records=$((data_lines - 1))
        echo -e "${GREEN} Test Data: jmeter_testdata.csv found (${data_records} records)${NC}"
    else
        echo -e "${YELLOW} Test Data: jmeter_testdata.csv not found${NC}"
        echo -e "${YELLOW}Creating basic test data...${NC}"

        cat > jmeter_testdata.csv << EOF
customerId,customerName,customerEmail,customerPhone,accountId,accountNumber,accountType,balance
1,Test Customer,test@microbank360.com,555-0001,1,ACC001,SAVINGS,1000.00
2,Demo User,demo@microbank360.com,555-0002,2,ACC002,CURRENT,2000.00
EOF

        echo -e "${GREEN} Basic test data created${NC}"
    fi
}

test_service() {
    local service_name="$1"
    local endpoint="$2"

    if response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 "${endpoint}" 2>/dev/null); then
        if [ "$response" = "200" ]; then
            echo -e "${GREEN} ${service_name}: UP (Status: ${response})${NC}"
            return 0
        else
            echo -e "${YELLOW} ${service_name}: Unexpected status ${response}${NC}"
            return 1
        fi
    else
        echo -e "${RED} ${service_name}: DOWN - Connection failed${NC}"
        return 1
    fi
}

check_services() {
    echo ""
    echo -e "${YELLOW} MicroBank360 Services Health Check:${NC}"
    echo "======================================"

    local healthy_services=0
    local total_services=3

    if test_service "API Gateway" "http://${BASE_URL}:${PORT}/seed/health"; then
        ((healthy_services++))
    fi

    if test_service "Customer Service" "http://${BASE_URL}:${PORT}/customer/1"; then
        ((healthy_services++))
    fi

    if test_service "Account Service" "http://${BASE_URL}:${PORT}/account/1"; then
        ((healthy_services++))
    fi

    if [ $healthy_services -eq $total_services ]; then
        echo -e "${GREEN} All services are healthy (${healthy_services}/${total_services})${NC}"
    elif [ $healthy_services -gt 0 ]; then
        echo -e "${YELLOW} Some services are down (${healthy_services}/${total_services} healthy)${NC}"
        echo -e "${YELLOW}Test will continue but results may be affected${NC}"
    else
        echo -e "${RED} All services are down! Cannot proceed with testing${NC}"
        echo -e "${RED}Please start your MicroBank360 services and try again${NC}"
        exit 1
    fi
}

create_results_directory() {
    echo ""
    echo -e "${YELLOW} Creating Results Directory...${NC}"
    mkdir -p "${RESULT_DIR}"
    echo -e "${GREEN} Results will be saved to: ${RESULT_DIR}${NC}"
}

run_performance_test() {
    echo ""
    echo -e "${GREEN} Starting Quick Performance Test...${NC}"
    echo "======================================"
    echo "Start Time: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Estimated Duration: $((DURATION + RAMP_UP + 30)) seconds"
    echo ""

    local jmeter_args=(
        "-n"
        "-t" "${JMX_FILE}"
        "-l" "${RESULT_DIR}/test-results.jtl"
        "-e" "-o" "${RESULT_DIR}/html-report"
        "-JTHREADS=${THREADS}"
        "-JDURATION=${DURATION}"
        "-JRAMPUP=${RAMP_UP}"
        "-JBASE_URL=${BASE_URL}"
        "-JPORT=${PORT}"
    )

    echo -e "${GRAY}Executing: ${JMETER_CMD} ${jmeter_args[*]}${NC}"
    echo ""

    if "${JMETER_CMD}" "${jmeter_args[@]}"; then
        echo ""
        echo -e "${GREEN}Test completed at: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
        return 0
    else
        echo ""
        echo -e "${RED} Test execution failed!${NC}"
        return 1
    fi
}

analyze_results() {
    echo ""
    echo -e "${CYAN} Quick Results Analysis:${NC}"
    echo "========================="

    if [ -f "${RESULT_DIR}/test-results.jtl" ]; then
        local total_requests=$(tail -n +2 "${RESULT_DIR}/test-results.jtl" | wc -l)

        if [ $total_requests -gt 0 ]; then
            echo -e "${GREEN} Test executed successfully!${NC}"
            echo " Total API requests: ${total_requests}"

            # Calculate success rate
            local successful_requests=$(grep -c ",true," "${RESULT_DIR}/test-results.jtl" || echo "0")
            local failed_requests=$((total_requests - successful_requests))
            local success_rate=0

            if [ $total_requests -gt 0 ]; then
                success_rate=$(( (successful_requests * 100) / total_requests ))
            fi

            echo " Successful requests: ${successful_requests}"
            echo " Failed requests: ${failed_requests}"
            echo " Success rate: ${success_rate}%"

            # Performance rating
            if [ $success_rate -ge 95 ]; then
                echo -e "${GREEN} Performance Rating: EXCELLENT${NC}"
            elif [ $success_rate -ge 90 ]; then
                echo -e "${GREEN} Performance Rating: GOOD${NC}"
            elif [ $success_rate -ge 80 ]; then
                echo -e "${YELLOW} Performance Rating: ACCEPTABLE${NC}"
            else
                echo -e "${RED} Performance Rating: NEEDS ATTENTION${NC}"
            fi

            # Calculate throughput
            local throughput=$((total_requests / DURATION))
            echo "Average Throughput: ${throughput} requests/second"

        else
            echo -e "${RED} No test data generated - possible configuration issue${NC}"
            return 1
        fi
    else
        echo -e "${RED} Results file not found - test may have failed${NC}"
        return 1
    fi
}

generate_report() {
    if [ -f "${RESULT_DIR}/html-report/index.html" ]; then
        echo ""
        echo -e "${GREEN} HTML Report Generated Successfully!${NC}"
        echo "Report Location: ${RESULT_DIR}/html-report/index.html"

        # Attempt to open report (works on macOS and some Linux distributions)
        if command -v open >/dev/null 2>&1; then
            echo -e "${YELLOW} Opening performance report in browser...${NC}"
            open "${RESULT_DIR}/html-report/index.html" 2>/dev/null || true
        elif command -v xdg-open >/dev/null 2>&1; then
            echo -e "${YELLOW} Opening performance report in browser...${NC}"
            xdg-open "${RESULT_DIR}/html-report/index.html" 2>/dev/null || true
        else
            echo -e "${YELLOW} Please open manually: ${RESULT_DIR}/html-report/index.html${NC}"
        fi
    else
        echo -e "${YELLOW} HTML report not generated (JTL file may be empty)${NC}"
    fi
}

print_summary() {
    echo ""
    echo -e "${CYAN} Test Summary:${NC}"
    echo "================"
    echo "Test Type: Quick Performance Validation"
    echo "Target System: MicroBank360 Banking APIs"
    echo "Test Duration: ${DURATION} seconds"
    echo "Virtual Users: ${THREADS}"
    echo "Results Location: ${RESULT_DIR}"
    echo "Completion Time: $(date '+%Y-%m-%d %H:%M:%S')"

    echo ""
    echo -e "${GREEN} Quick test completed! Thank you, ${SCRIPT_AUTHOR}!${NC}"
    echo -e "${CYAN}For comprehensive testing, run: ./run-microbank360-tests.sh${NC}"
}

# Main Execution Flow
main() {
    print_header
    print_config
    check_requirements
    check_services
    create_results_directory

    if run_performance_test; then
        if analyze_results; then
            generate_report
        fi
    fi

    print_summary
}

# Execute main function
main "$@"