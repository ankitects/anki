#!/bin/bash
# 
# A set of utility functions for shell scripts relating to git.

. bash-util.sh || exit 1


# Outputs the body of the commit message for a given commit.
#
# arguments:
#   commit: the SHA of the commit
#
# stdout:
#   the body of the commit message
function git_commit_body() {
  local commit="$1"; shift || missing_arg commit
  no_more_args "$@"

  git log -n1 --format="%B" "$commit"
}


# Outputs the SHAs of the commits up to the given one that are not yet
# integrated into the base commit.
#
# arguments:
#   commit: the SHA of the commit to integrate
#   base: the SHA of the base commit to integrate into
#
# Outputs:
#   the abbreviated SHAs of all commits that still need to be integrated
function git_commits_not_in() {
  local commit="$1"; shift || missing_arg commit
  local base="$1"; shift || missing_arg base
  no_more_args "$@"

  git log --format='%h' --first-parent $base..$commit
}


# Outputs the abbreviated SHA for the given commit.
#
# arguments:
#   commit: the SHA of a commit
#
# stdout:
#   the abbreviated SHA of the commit
function git_abbrev() {
  local commit="$1"; shift
  test $# = 0

  git log -n1 --format=%h "$commit"
}


# Commits the current merge using a specified message.
#
# arguments:
#   passed to 'git commit' itself
#
# stdin:
#   used as the commit message
function git_commit_merge_with_message() {
  # Copy the message to the merge message file.
  cat - >"$(git rev-parse --git-dir)/MERGE_MSG"
  # And commit the changes without invoking the editor.
  EDITOR=true git commit "$@"
}


# Returns true if the given branch exists.
#
# arguments:
#   branch: the name of a possible branch
#
# return:
#   zero if the branch exists, non-zero otherwise
function git_is_branch() {
  local branch="$1"; shift || missing_arg branch
  no_more_args "$@"

  git for-each-ref --format='%(refname:short)' refs/heads/ |
      silently grep "^$branch\$"
}
