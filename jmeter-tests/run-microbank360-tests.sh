#!/bin/bash

#===============================================================================
# MicroBank360 Comprehensive Performance Test Suite
#===============================================================================
# Description: Professional performance testing suite for MicroBank360 Banking Microservices
# Created by:  PritiAryal
# Date:        2025-07-20 03:55:00 EST
# Version:     1.0
#
# Usage Examples:
#   ./run-microbank360-tests.sh                      # Run all tests
#   ./run-microbank360-tests.sh baseline             # Run only baseline test
#   ./run-microbank360-tests.sh load localhost 8085  # Run load test
#===============================================================================

set -euo pipefail

# Script Configuration
readonly SCRIPT_VERSION="1.0"
readonly SCRIPT_AUTHOR="PritiAryal"
readonly SCRIPT_DATE="2025-07-20 07:47:05 EST"
readonly JMX_FILE="microbank360-performance-test.jmx"

# Command line parameters
TEST_SUITE=${1:-"all"}
BASE_URL=${2:-"localhost"}
PORT=${3:-8085}
OUTPUT_DIR=${4:-"test-results"}

# Test Scenarios Configuration
declare -A TEST_SCENARIOS_NAMES=(
    ["baseline"]="Baseline Test"
    ["load"]="Load Test"
    ["stress"]="Stress Test"
    ["spike"]="Spike Test"
)

declare -A TEST_SCENARIOS_DESC=(
    ["baseline"]="Baseline performance with minimal load"
    ["load"]="Normal expected production load"
    ["stress"]="High load stress testing"
    ["spike"]="Sudden traffic spike simulation"
)

declare -A TEST_SCENARIOS_THREADS=(
    ["baseline"]=10
    ["load"]=25
    ["stress"]=100
    ["spike"]=200
)

declare -A TEST_SCENARIOS_DURATION=(
    ["baseline"]=180
    ["load"]=300
    ["stress"]=300
    ["spike"]=120
)

declare -A TEST_SCENARIOS_RAMPUP=(
    ["baseline"]=30
    ["load"]=60
    ["stress"]=90
    ["spike"]=20
)

declare -A TEST_SCENARIOS_EXPECTED_TPS=(
    ["baseline"]=5
    ["load"]=15
    ["stress"]=50
    ["spike"]=100
)

# Global result storage
declare -A TEST_RESULTS_SUCCESS
declare -A TEST_RESULTS_TOTAL_REQUESTS
declare -A TEST_RESULTS_SUCCESS_REQUESTS
declare -A TEST_RESULTS_FAILED_REQUESTS
declare -A TEST_RESULTS_SUCCESS_RATE
declare -A TEST_RESULTS_TPS
declare -A TEST_RESULTS_GRADE
declare -A TEST_RESULTS_HAS_HTML_REPORT
declare -A TEST_RESULTS_ERROR

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly WHITE='\033[1;37m'
readonly GRAY='\033[0;37m'
readonly NC='\033[0m' # No Color

# Helper Functions
print_header() {
    clear
    echo -e "${CYAN}"
    echo "🏦 MicroBank360 Professional Performance Test Suite"
    echo "=========================================================="
    echo -e "Created by: ${SCRIPT_AUTHOR}"
    echo -e "Version: ${SCRIPT_VERSION}"
    echo -e "Date: ${SCRIPT_DATE}"
    echo -e "Current Time: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo -e "${NC}"
}

print_suite_config() {
    echo -e "${YELLOW}🎯 Test Suite Configuration:${NC}"
    echo "============================="
    echo "Target System: http://${BASE_URL}:${PORT}"
    echo "Test Suite: ${TEST_SUITE}"
    echo "Output Directory: ${OUTPUT_DIR}"
    echo "JMeter Test Plan: ${JMX_FILE}"
    echo ""
}

determine_tests_to_run() {
    if [ "${TEST_SUITE}" = "all" ]; then
        TESTS_TO_RUN=("baseline" "load" "stress" "spike")
    else
        # Validate test suite parameter
        if [[ ! "${TEST_SCENARIOS_NAMES[$TEST_SUITE]:-}" ]]; then
            echo -e "${RED}❌ Invalid test suite: ${TEST_SUITE}${NC}"
            echo -e "${YELLOW}Valid options: all, baseline, load, stress, spike${NC}"
            exit 1
        fi
        TESTS_TO_RUN=("${TEST_SUITE}")
    fi
}

print_planned_tests() {
    echo -e "${YELLOW}📋 Planned Test Scenarios:${NC}"
    echo "=========================="
    for test in "${TESTS_TO_RUN[@]}"; do
        local threads=${TEST_SCENARIOS_THREADS[$test]}
        local duration=${TEST_SCENARIOS_DURATION[$test]}
        local minutes=$((duration / 60))
        echo "• ${TEST_SCENARIOS_NAMES[$test]}: ${threads} users, ${minutes} min"
    done
    echo ""
}

#check_requirements() {
#    echo -e "${YELLOW}🔍 Pre-flight System Checks:${NC}"
#    echo "============================="
#
#    # Check JMeter installation
#    if command -v jmeter >/dev/null 2>&1; then
#        JMETER_CMD="jmeter"
#        echo -e "${GREEN}✅ JMeter: Found in system PATH${NC}"
#    elif [ -f "apache-jmeter-"*/bin/jmeter ]; then
#        JMETER_CMD="$(find . -name "jmeter" -path "*/apache-jmeter-*/bin/jmeter" | head -1)"
#        echo -e "${GREEN}✅ JMeter: Found locally${NC}"
#    else
#        echo -e "${RED}❌ JMeter: Not found!${NC}"
#        echo -e "${RED}Please install JMeter or ensure it's available in PATH${NC}"
#        exit 1
#    fi
#
#    # Check required files
#    local required_files=("${JMX_FILE}" "jmeter_testdata.csv")
#    local missing_files=()
#
#    for file in "${required_files[@]}"; do
#        if [ -f "${file}" ]; then
#            echo -e "${GREEN}✅ Required File: ${file} found${NC}"
#        else
#            echo -e "${RED}❌ Required File: ${file} missing!${NC}"
#            missing_files+=("${file}")
#        fi
#    done
#
#    if [ ${#missing_files[@]} -gt 0 ]; then
#        echo -e "${RED}Cannot proceed - missing required files: ${missing_files[*]}${NC}"
#        exit 1
#    fi
#}

check_requirements() {
    echo -e "${YELLOW}🔍 Pre-flight System Checks:${NC}"
    echo "============================="

    # Check JMeter installation
    if command -v jmeter >/dev/null 2>&1; then
        JMETER_CMD="jmeter"
        echo -e "${GREEN}✅ JMeter: Found in system PATH${NC}"
    else
        # Check for local JMeter installation using a for loop
        JMETER_CMD=""
        for jmeter_path in apache-jmeter-*/bin/jmeter; do
            if [ -f "$jmeter_path" ]; then
                JMETER_CMD="$jmeter_path"
                echo -e "${GREEN}✅ JMeter: Found locally (${JMETER_CMD})${NC}"
                break
            fi
        done

        # If still not found, try batch file for Windows
        if [ -z "$JMETER_CMD" ]; then
            for jmeter_path in apache-jmeter-*/bin/jmeter.bat; do
                if [ -f "$jmeter_path" ]; then
                    JMETER_CMD="$jmeter_path"
                    echo -e "${GREEN}✅ JMeter: Found locally (${JMETER_CMD})${NC}"
                    break
                fi
            done
        fi

        # Final check - if still not found, exit
        if [ -z "$JMETER_CMD" ]; then
            echo -e "${RED}❌ JMeter: Not found!${NC}"
            echo -e "${RED}Please install JMeter or ensure it's available in PATH${NC}"
            echo -e "${YELLOW}Expected: apache-jmeter-*/bin/jmeter or jmeter in PATH${NC}"
            exit 1
        fi
    fi

    # Check required files
    local required_files=("${JMX_FILE}" "jmeter_testdata.csv")
    local missing_files=()

    for file in "${required_files[@]}"; do
        if [ -f "${file}" ]; then
            echo -e "${GREEN}✅ Required File: ${file} found${NC}"
        else
            echo -e "${RED}❌ Required File: ${file} missing!${NC}"
            missing_files+=("${file}")
        fi
    done

    if [ ${#missing_files[@]} -gt 0 ]; then
        echo -e "${RED}Cannot proceed - missing required files: ${missing_files[*]}${NC}"
        exit 1
    fi
}

test_service_health() {
    local service_name="$1"
    local endpoint="$2"

    if curl -s --connect-timeout 10 "${endpoint}" >/dev/null 2>&1; then
        echo -e "${GREEN}✅ ${service_name}: UP${NC}"
        return 0
    else
        echo -e "${RED}❌ ${service_name}: DOWN${NC}"
        return 1
    fi
}

check_service_health() {
    echo ""
    echo -e "${YELLOW}🔍 MicroBank360 Services Health Check:${NC}"
    echo "======================================"

    local healthy_services=0
    local total_services=3

    if test_service_health "API Gateway" "http://${BASE_URL}:${PORT}/seed/health"; then
        ((healthy_services++))
    fi

    if test_service_health "Customer Service" "http://${BASE_URL}:${PORT}/customer/1"; then
        ((healthy_services++))
    fi

    if test_service_health "Account Service" "http://${BASE_URL}:${PORT}/account/1"; then
        ((healthy_services++))
    fi

    if [ $healthy_services -ne $total_services ]; then
        echo -e "${YELLOW}⚠️ Warning: $((total_services - healthy_services)) out of ${total_services} services are down${NC}"
        echo -e "${YELLOW}Test results may be affected. Continue anyway? (y/N)${NC}"
        read -r continue_response
        if [[ ! "$continue_response" =~ ^[Yy]$ ]]; then
            echo -e "${YELLOW}Test cancelled by user${NC}"
            exit 0
        fi
    fi
}

create_master_results_directory() {
    local master_timestamp=$(date +"%Y%m%d_%H%M%S")
    MASTER_RESULT_DIR="${OUTPUT_DIR}/microbank360-testsuite-${master_timestamp}"
    mkdir -p "${MASTER_RESULT_DIR}"

    echo ""
    echo -e "${GREEN}📁 Master Results Directory: ${MASTER_RESULT_DIR}${NC}"
    echo ""
}

execute_performance_test() {
    local test_name="$1"
    local results_dir="$2"

    local test_display_name=${TEST_SCENARIOS_NAMES[$test_name]}
    local test_description=${TEST_SCENARIOS_DESC[$test_name]}
    local threads=${TEST_SCENARIOS_THREADS[$test_name]}
    local duration=${TEST_SCENARIOS_DURATION[$test_name]}
    local ramp_up=${TEST_SCENARIOS_RAMPUP[$test_name]}
    local expected_tps=${TEST_SCENARIOS_EXPECTED_TPS[$test_name]}

    echo ""
    echo -e "${CYAN}🚀 Executing: ${test_display_name}${NC}"
    echo "=================================================="
    echo "Description: ${test_description}"
    echo "Configuration:"
    echo "  • Virtual Users: ${threads}"
    echo "  • Duration: ${duration} seconds ($((duration / 60)) minutes)"
    echo "  • Ramp-up: ${ramp_up} seconds"
    echo "  • Expected TPS: ~${expected_tps}"
    echo "  • Results: ${results_dir}"
    echo ""

    local start_time=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${YELLOW}⏰ Start Time: ${start_time}${NC}"
    echo ""

    # JMeter execution
    local jmeter_args=(
        "-n"
        "-t" "${JMX_FILE}"
        "-l" "${results_dir}/test-results.jtl"
        "-e" "-o" "${results_dir}/html-report"
        "-JTHREADS=${threads}"
        "-JDURATION=${duration}"
        "-JRAMPUP=${ramp_up}"
        "-JBASE_URL=${BASE_URL}"
        "-JPORT=${PORT}"
    )

    if "${JMETER_CMD}" "${jmeter_args[@]}"; then
        local end_time=$(date '+%Y-%m-%d %H:%M:%S')
        echo ""
        echo -e "${YELLOW}⏰ End Time: ${end_time}${NC}"

        # Analyze results
        if analyze_test_results "$test_name" "$results_dir" "$duration" "$expected_tps"; then
            return 0
        else
            return 1
        fi
    else
        echo ""
        echo -e "${RED}❌ JMeter execution failed${NC}"
        TEST_RESULTS_SUCCESS[$test_name]="false"
        TEST_RESULTS_ERROR[$test_name]="JMeter execution failed"
        return 1
    fi
}

analyze_test_results() {
    local test_name="$1"
    local results_dir="$2"
    local duration="$3"
    local expected_tps="$4"

    if [ -f "${results_dir}/test-results.jtl" ]; then
        local total_requests=$(tail -n +2 "${results_dir}/test-results.jtl" | wc -l)

        if [ $total_requests -gt 0 ]; then
            local successful_requests=$(grep -c ",true," "${results_dir}/test-results.jtl" || echo "0")
            local failed_requests=$((total_requests - successful_requests))
            local success_rate=0
            local actual_tps=0

            if [ $total_requests -gt 0 ]; then
                success_rate=$(( (successful_requests * 100) / total_requests ))
                actual_tps=$((total_requests / duration))
            fi

            echo ""
            echo -e "${CYAN}📊 Test Results Summary:${NC}"
            echo "========================"
            echo "✅ Total Requests: ${total_requests}"
            echo "✅ Successful: ${successful_requests} (${success_rate}%)"
            echo "❌ Failed: ${failed_requests}"
            echo "📈 Throughput: ${actual_tps} TPS (Expected: ~${expected_tps})"

            # Performance evaluation
            local performance_grade
            if [ $success_rate -ge 99 ]; then
                performance_grade="A+"
            elif [ $success_rate -ge 95 ]; then
                performance_grade="A"
            elif [ $success_rate -ge 90 ]; then
                performance_grade="B"
            elif [ $success_rate -ge 80 ]; then
                performance_grade="C"
            else
                performance_grade="F"
            fi

            case "$performance_grade" in
                "A+"|"A") echo -e "${GREEN}🎯 Performance Grade: ${performance_grade}${NC}" ;;
                "B"|"C") echo -e "${YELLOW}🎯 Performance Grade: ${performance_grade}${NC}" ;;
                *) echo -e "${RED}🎯 Performance Grade: ${performance_grade}${NC}" ;;
            esac

            # Store results for summary
            TEST_RESULTS_SUCCESS[$test_name]="true"
            TEST_RESULTS_TOTAL_REQUESTS[$test_name]=$total_requests
            TEST_RESULTS_SUCCESS_REQUESTS[$test_name]=$successful_requests
            TEST_RESULTS_FAILED_REQUESTS[$test_name]=$failed_requests
            TEST_RESULTS_SUCCESS_RATE[$test_name]=$success_rate
            TEST_RESULTS_TPS[$test_name]=$actual_tps
            TEST_RESULTS_GRADE[$test_name]=$performance_grade
            TEST_RESULTS_HAS_HTML_REPORT[$test_name]=$([ -f "${results_dir}/html-report/index.html" ] && echo "true" || echo "false")

            return 0
        else
            echo ""
            echo -e "${RED}❌ No test data generated - possible configuration issue${NC}"
            TEST_RESULTS_SUCCESS[$test_name]="false"
            TEST_RESULTS_ERROR[$test_name]="No test data generated"
            return 1
        fi
    else
        echo ""
        echo -e "${RED}❌ Results file not found - test execution failed${NC}"
        TEST_RESULTS_SUCCESS[$test_name]="false"
        TEST_RESULTS_ERROR[$test_name]="Results file not found"
        return 1
    fi
}

execute_test_suite() {
    echo -e "${GREEN}🎬 Starting Test Suite Execution...${NC}"
    echo ""

    local overall_start_time=$(date '+%Y-%m-%d %H:%M:%S')

    for test_name in "${TESTS_TO_RUN[@]}"; do
        local test_result_dir="${MASTER_RESULT_DIR}/${test_name}-test"
        mkdir -p "${test_result_dir}"

        execute_performance_test "${test_name}" "${test_result_dir}"

        # Brief pause between tests (except for the last test)
        if [ "${test_name}" != "${TESTS_TO_RUN[-1]}" ]; then
            echo ""
            echo -e "${YELLOW}⏸️ Pausing 30 seconds before next test...${NC}"
            sleep 30
        fi
    done

    local overall_end_time=$(date '+%Y-%m-%d %H:%M:%S')

    generate_master_summary_report "$overall_start_time" "$overall_end_time"
}

generate_master_summary_report() {
    local start_time="$1"
    local end_time="$2"

    echo ""
    echo ""
    echo -e "${CYAN}📋 MASTER TEST SUITE SUMMARY REPORT${NC}"
    echo "============================================================"
    echo "Test Suite: MicroBank360 Performance Testing"
    echo "Executed by: ${SCRIPT_AUTHOR}"
    echo "Target System: http://${BASE_URL}:${PORT}"
    echo "Execution Time: ${start_time} - ${end_time}"
    echo ""

    # Individual Test Results
    echo -e "${YELLOW}📊 Individual Test Results:${NC}"
    echo "============================"

    local successful_tests=0
    local total_tests=${#TESTS_TO_RUN[@]}

    for test_name in "${TESTS_TO_RUN[@]}"; do
        local test_display_name=${TEST_SCENARIOS_NAMES[$test_name]}

        echo ""
        echo -e "${WHITE}🧪 ${test_display_name}:${NC}"

        if [ "${TEST_RESULTS_SUCCESS[$test_name]:-false}" = "true" ]; then
            echo -e "   Status: ${GREEN}✅ SUCCESS${NC}"
            echo -e "   Requests: ${TEST_RESULTS_TOTAL_REQUESTS[$test_name]} total, ${TEST_RESULTS_SUCCESS_REQUESTS[$test_name]} successful"
            echo -e "   Success Rate: ${TEST_RESULTS_SUCCESS_RATE[$test_name]}%"
            echo -e "   Throughput: ${TEST_RESULTS_TPS[$test_name]} TPS"
            echo -e "   Grade: ${TEST_RESULTS_GRADE[$test_name]}"

            if [ "${TEST_RESULTS_HAS_HTML_REPORT[$test_name]:-false}" = "true" ]; then
                echo -e "   Report: ${MASTER_RESULT_DIR}/${test_name}-test/html-report/index.html"
            fi

            ((successful_tests++))
        else
            echo -e "   Status: ${RED}❌ FAILED${NC}"
            echo -e "   Error: ${TEST_RESULTS_ERROR[$test_name]:-Unknown error}"
        fi
    done

    # Overall Assessment
    echo ""
    echo -e "${YELLOW}🎯 Overall Assessment:${NC}"
    echo "======================"
    echo "Tests Completed: ${successful_tests}/${total_tests}"

    if [ $successful_tests -eq $total_tests ]; then
        echo -e "${GREEN}🏆 EXCELLENT: All tests completed successfully!${NC}"
        echo -e "${GREEN}Your MicroBank360 system demonstrates solid performance characteristics.${NC}"
    elif [ $successful_tests -gt 0 ]; then
        echo -e "${YELLOW}⚠️ PARTIAL: $((total_tests - successful_tests)) test(s) failed${NC}"
        echo -e "${YELLOW}Review failed tests and system configuration.${NC}"
    else
        echo -e "${RED}🔴 CRITICAL: All tests failed${NC}"
        echo -e "${RED}System requires immediate attention before production deployment.${NC}"
    fi

    # Generate summary file
    local summary_file="${MASTER_RESULT_DIR}/test-summary.txt"
    cat > "${summary_file}" << EOF
MicroBank360 Performance Test Suite Summary
===========================================
Executed by: ${SCRIPT_AUTHOR}
Execution Time: ${start_time} - ${end_time}
Target: http://${BASE_URL}:${PORT}
Tests Completed: ${successful_tests}/${total_tests}

Individual Results:
$(for test_name in "${TESTS_TO_RUN[@]}"; do
    if [ "${TEST_RESULTS_SUCCESS[$test_name]:-false}" = "true" ]; then
        echo "${TEST_SCENARIOS_NAMES[$test_name]}: SUCCESS - ${TEST_RESULTS_SUCCESS_RATE[$test_name]}% success, ${TEST_RESULTS_TPS[$test_name]} TPS"
    else
        echo "${TEST_SCENARIOS_NAMES[$test_name]}: FAILED - ${TEST_RESULTS_ERROR[$test_name]:-Unknown error}"
    fi
done)
EOF

    echo ""
    echo -e "${GRAY}📄 Summary saved to: ${summary_file}${NC}"

    # Try to open first available report
    for test_name in "${TESTS_TO_RUN[@]}"; do
        if [ "${TEST_RESULTS_SUCCESS[$test_name]:-false}" = "true" ] && [ "${TEST_RESULTS_HAS_HTML_REPORT[$test_name]:-false}" = "true" ]; then
            local report_path="${MASTER_RESULT_DIR}/${test_name}-test/html-report/index.html"
            echo ""
            echo -e "${YELLOW}🌐 Available HTML Report: ${report_path}${NC}"
            break
        fi
    done

    echo ""
    echo -e "${GREEN}🎉 Performance Test Suite Completed!${NC}"
    echo -e "${CYAN}Thank you for using MicroBank360 Performance Test Suite, ${SCRIPT_AUTHOR}!${NC}"
    echo -e "${CYAN}Results Location: ${MASTER_RESULT_DIR}${NC}"
}

# Main Execution Flow
main() {
    print_header
    print_suite_config
    determine_tests_to_run
    print_planned_tests
    check_requirements
    check_service_health
    create_master_results_directory
    execute_test_suite
}

# Execute main function
main "$@"