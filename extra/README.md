# Runnables

## Annotating text

### Configuration

-  In `extra/src/main/resources/application.conf`, specify a value for `odinson.extra.processorType`.

-  Ensure `odinson.textDir` and `odinson.docDir` are set as intended.  Text will be read from `odinson.textDir`, annotated, and serialized to `odinson.docDir`.

### Command

```
sbt ";project extra;runMain ai.lum.odinson.extra.AnnotateText"
```

## Indexing annotated documents

### Configuration

- Ensure the `odinson.docDir` directory contains annotated and serialized `org.clulab.processors.Document`s (`.json` or `.ser`).
- Ensure `odinson.indexDir` is correct.  The odinson index will be created at this location.

### Command

```
sbt "extra/runMain ai.lum.odinson.extra.IndexDocuments"
```

## Querying using the shell

### Configuration

- Ensure `odinson.indexDir` is correct.  The odinson index will be read from this location.

### Command

```
sbt "extra/runMain ai.lum.odinson.extra.Shell"
```

### Example queries

- `[tag="NNP"]+`
  - Find a sequence of 1 or more proper nouns.

- `(?<predicate>[]) >nsubjpass (?<patient>[])`
  - Find passive constructions and capture both the "predicate" and "patient".


- `(?<theme>[]) <dobj (?<predicate>[]) >nsubj (?<agent>[])`
  - Find subject-verb-object triples and capture subject as "agent", object as "theme" and the predicate as "predicate".




