#!/bin/bash
#
# A set of utility function for bash scripts.


# Executes the given command, discarding any output to stderr.
#
# arguments:
#   the command to execute
#
# stdout:
#   the output of the command
#
# return:
#   the return value of the command
function quietly() {
  "$@" 2>/dev/null
}


# Executes the given command, discarding any output to stdout or stderr.
#
# arguments:
#   the command to execute
#
# return:
#   the return value of the command
function silently() {
  "$@" 2>/dev/null >/dev/null
}


# Outputs an error message and exits the script.
#
# arguments:
#   message: one or more strings, the message to be printed
#
# stderr:
#   the message
function fatal() {
  echo "FATAL: $*" 1>&2
  exit 113  # Use a special error code so that we can identify fatal failures.
}


# Output an error message for a missing argument and terminates with a fatal
# error.
#
# arguments:
#   name: the name of the missing argument
#
# stderr:
#   an error message for the missing argument (to stderr)
function missing_arg() {
  fatal "Missing argument: $1"
}


# Output an error message if given any arguments and (in that case) also
# terminates with a fatal error.
#
# arguments:
#   arguments: the remaining arguments to the function
function no_more_args() {
  test $# = 0 || fatal "Too many arguments: $*"
}
