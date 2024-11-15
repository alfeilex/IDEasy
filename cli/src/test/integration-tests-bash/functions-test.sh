#!/bin/bash
#set -x
#set -e errexit

WORK_DIR_INTEG_TEST="/c/tmp/ideasy-integration-test-debug/IDEasy_snapshot"
IDEASY_COMPRESSED_NAME="ideasy_latest.tar.gz"
IDEASY_COMPRESSED_FILE="${WORK_DIR_INTEG_TEST}/${IDEASY_COMPRESSED_NAME}"
test_project_name="tmp-integ-test"

function doIdeCreate () {
  #TODO: determine the name of the currently executed script
#  local project-name="$(dirname "${BASH_SOURCE:-$0}")" 
#  local project_name="$(basename "${BASH_SOURCE:-$0}")"
  #  local test_project_name="tmp-integ-test"
  # If first argument is given, then it is the url for the ide create command (default is '-').
  local settings_url=${1:--}
  $IDE create ${test_project_name} ${settings_url}
  echo ide create ${test_project_name} ${settings_url}
  #TODO: IDE_ROOT ?
  # mkdir ${IDE_ROOT}/${test_project_name}
  cd "${IDE_ROOT}/${test_project_name}"

  echo "${IDE_ROOT}/${test_project_name}"
  echo "${test_project_name}"
  # TODO: Remove logs
  echo "My IDE_ROOT is: ${IDE_ROOT}"
  echo "My PWD is: $PWD"
  echo "settings-url : ${settings_url}"
}

function doIdeCreateCleanup () {
    rm -rf ${IDE_ROOT}/${test_project_name}
}

function doDownloadSnapshot () {
    mkdir -p $WORK_DIR_INTEG_TEST
    if [ $1 ]
    then
	if [ -f "${1}" ] && [[ $1 == *.tar.gz ]];
	then
	    echo "Local snapshot given. Copy to directory ${WORK_DIR_INTEG_TEST}"
	    cp "$1" "${IDEASY_COMPRESSED_FILE}" 
	else
	    echo "Expected a file ending with tar.gz - Given: ${1}"
	    exit 1
	fi
    else
	echo "Will try to download latest IDEasy release..."
	local URL_IDEASY_LATEST="https://github.com/devonfw/IDEasy/releases/latest"
	local PAGE_HTML_LOCAL="${WORK_DIR_INTEG_TEST}/integ_test_gh_latest.html"
	
	curl -L $URL_IDEASY_LATEST  > $PAGE_HTML_LOCAL
	# TODO: A bit of a workaround. But works for the time being...
	# Note: Explanation for cryptic argument "\"" of 'cut': delimiting char after url link from href is char '"'
	local URL=$(cat $PAGE_HTML_LOCAL | grep "href=\"https://.*windows-x64.tar.gz" | grep -o https://.*windows-x64.tar.gz | cut -f1 -d"\"")
	curl -o "${IDEASY_COMPRESSED_FILE}" $URL
	rm $PAGE_HTML_LOCAL
    fi
}


function doExtract() {
  echo "${IDE_ROOT}/_ide"
  if [ -f "${IDEASY_COMPRESSED_FILE}" ]
  then
    tar xfz "${IDEASY_COMPRESSED_FILE}" --directory "${IDE_ROOT}/_ide" || exit 1
  else
    echo "Could not find and extract release ${IDEASY_COMPRESSED_FILE}"
    exit 1
  fi
}

# $@: success message
function doSuccess() {
  echo -e "\033[92m${*}\033[39m"
}

# $@: warning message
function doWarning() {
  echo -e "\033[93m${*}\033[39m"
}

# $@: messages to output
function doError() {
  echo -e "\033[91m${1}\033[39m"
}

function doIsMacOs() {
  if [ "${OSTYPE:0:6}" = "darwin" ]
  then
    return
  fi
  return 255
}

function doIsWindows() {
  if [ "${OSTYPE}" = "cygwin" ] || [ "${OSTYPE}" = "msys" ]
  then
    return
  fi
  return 255
}
