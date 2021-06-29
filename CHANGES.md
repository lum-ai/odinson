# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Added tags-vocabulary endpoint to API for index-specific part-of-speech tags.
- Added tests for metadata and parent API calls in backend.
### Changed
- Parent document filenames are stored by default.

## [0.4.0] - 2021-06-07
### Added
- Added a histogram endpoint for term frequencies.
- Enhanced term-freq endpoint to allow filtering as well as grouping by a second field.
- Added ability to Mentions to populate their lexical content ([#274](https://github.com/lum-ai/odinson/pull/274))
- Added tests for parent queries in core and backend.
### Changed
- Dependencies now stored as BinaryDocValuesField (previously SortedDocValuesField) to allow for larger graphs ([#283](https://github.com/lum-ai/odinson/pull/283)).
- Moved responsibility for getting lexical content from ExtractorEngine to DataGatherer ([#274](https://github.com/lum-ai/odinson/pull/274))
- Metadata is now indexed as TokensFields instead of StringFields.

## [0.3.0] - 2021-02-18
### Added 
- Added :mkDoc command to shell ([#272](https://github.com/lum-ai/odinson/pull/272))
- Added ability to serialize Mentions verbosely (with displayField or all storedFields) ([#265](https://github.com/lum-ai/odinson/pull/265))
- Added project-wide formatting settings and a PR check for linting
- Added a file that accompanies index (`settings.json`) that describes settings used in creating the index.  Currently storing `storedFields`. ([#255](https://github.com/lum-ai/odinson/pull/255))
- Added REST API endpoint for returning frequencies of token-based annotations in a corpus.
- Added `ai.lum.odinson.utils.TestUtils` and the corresponding `OdinsonText` in the main project for using the test utils in dependent projects ([#232](https://github.com/lum-ai/odinson/pull/231))
- Added some additional methods to ExtractorEngine to access tokens from diff fields of a Lucene Doc ([#231](https://github.com/lum-ai/odinson/pull/231))
- Added json serialization and deserialization of Mentions and OdinsonMatches to core ([#226](https://github.com/lum-ai/odinson/pull/226))
- Added argument promotion, i.e., arguments specified for promotion or underspecified will be added to the state ([#218](https://github.com/lum-ai/odinson/pull/218))
- Add tests for REST API endpoints
- Grammar files now support imports of rules and variables, from both resources and filesystem; absolute and relative paths ([#175](https://github.com/lum-ai/odinson/pull/175), [#180](https://github.com/lum-ai/odinson/pull/180)).
- Validation of tokens to ensure they are compatible with Lucene ([#170](https://github.com/lum-ai/odinson/pull/170))
- Add priority as String to `Rule` and as `Priority` to `Extractor`
- Add `MentionFactory` to be optionally passed during construction of the `ExtractorEngine` so that custom `Mentions`
  can be produced.  Include a `DefaultMentionFactory` to be used if one isn't provided.  Change `Mention` to be a
  regular class instead of a case class to facilitate subclassing.
- Use added `State.addMentions` now instead of `State.addMention` with help of new `OdinResultsIterator` by [@kwalcock](https://github.com/kwalcock)
- Add `State` and `StateFactory` integration into `reference.conf` and integrate extras into `application.conf`
- Code coverage report.
- REST API endpoints for retrieving metadata and parent document; OpenAPI data model for `OdinsonDocument`, etc.
- Containerized Odinson
  - Docker images for [`extra`](https://hub.docker.com/r/lumai/odinson-extras) and the [REST API](https://hub.docker.com/r/lumai/odinson-rest-api) using the [`sbt-native-packager` plugin](https://github.com/sbt/sbt-native-packager).
- Added `ExtractorEngine.inMemory(...)` to help build an index in memory.
- Added `disableMatchSelector` to `ExtractorEngine.extractMentions()` to retrieve all spans of tokens that could
  be matched by the query. In other words, it skips the `MatchSelector`.
- Added `buildinfo.json` file to the index to store versions and build info.
- Added ability to express rule vars as lists, in addition to the current string representation.
- Put indexing docs in a method to be used by external projects. ([#90](https://github.com/lum-ai/odinson/pull/90))
- Started documentation at [http://gh.lum.ai/odinson/](http://gh.lum.ai/odinson/) ([#97](https://github.com/lum-ai/odinson/pull/97))
### Changed
- JsonSerializer is now a class, and has the ability to serialize verbose detail about Mentions ([#265](https://github.com/lum-ai/odinson/pull/265))
- updated version of CluLab processors in `extra/` to 8.2.3 ([#241](https://github.com/lum-ai/odinson/pull/241))
- using whole config to create ExtractorEngine and its components (rather than subconfigs) ([#231](https://github.com/lum-ai/odinson/pull/231))
- removed the MentionFactory, rename OdinMentionsIterator to MentionsIterator ([#228](https://github.com/lum-ai/odinson/pull/228))
- Different organization for tests. Now every test extends a `BaseSpec` class and there are 6 categories of tests.
- Turn `State` into a trait with very basic `SqlState` and even more basic `MemoryState` and placeholder `FileState` implementations by [@kwalcock](https://github.com/kwalcock)
- REST API: `/api/parent` -> `/api/parent/by-document-id` & `/api/parent/by-sentence-id`
- REST API: `sentId` param for `/api/sentence` -> `sentenceId`
- REST API: `rules` param for `/api/execute/grammar` -> `grammar`
- Retrieval of OdinsonSentence JSON via REST API
- `extra/AnnotateText` writes compressed json files
- Reduce number of array allocations
- All strings are normalized with NFKC, except the norm field which uses NFKC with casefolding,
  diacritic stripping, and some extra character mappings. This is the case both at index time and query time.
  This means you should reindex if you upgrade to this version.
### Fixed
- Use temporary directories for /extra and /backend tests to avoid the main index (`data/odinson/index`) being overwritten during testing
- Accept underscore at identifier start ([#209](https://github.com/lum-ai/odinson/pull/209))
- Nullpointer exception related to event arguments.
- size of roots array in `UnsafeSerializer`

## [0.2.3] - 2020-03-27
### Added
- Added option to allow arguments that overlap with the trigger in event mentions (disallowed by default)
- Added optional label to rules and mentions
- Added lucene segment information to `Mention`
- Added optional `label` support to named capture syntax, i.e. `(?<name:label> ... )`
- Added `QueryUtils.quantifier()` to make a quantifier string from some requirements, e.g. min and max repetitions.
### Fixed
- Enforce quantifier semantics in `event` rules.
- Replace variables in rule names
