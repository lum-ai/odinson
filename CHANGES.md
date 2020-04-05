# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Changed
- All strings are normalized with NFKC, except the norm field which uses NFKCCasefold.
  This is the case both at index time and query time. This means you should reindex
  if you upgrade to this version.

## [0.2.3] - 2020-03-27
### Fixed
- Enforce quantifier semantics in `event` rules.
- Replace variables in rule names
### Added
- Added option to allow arguments that overlap with the trigger in event mentions (disallowed by default)
- Added optional label to rules and mentions
- Added lucene segment information to `Mention`
- Added optional `label` support to named capture syntax, i.e. `(?<name:label> ... )`
- Added `QueryUtils.quantifier()` to make a quantifier string from some requirements, e.g. min and max repetitions.
