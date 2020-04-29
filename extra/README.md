# Runnables

## Annotating text
Before an Odinson index can be created, the text needs to be annotated.  
You may use your **own annotation tools**, as long as you convert your annotated output to [Odinson Documents](https://github.com/lum-ai/odinson/blob/master/core/src/main/scala/ai/lum/odinson/OdinsonDocument.scala).
However, we also provide an App for annotating free text and producing this format.

### Configuration
The configurations are specified in `extra/src/main/resources/application.conf`.

-  First, decide what Processor you'd like to use to annotate the text by specifying a value for `odinson.extra.processorType`.  Available options are `FastNLPProcessor`, `CluProcessor`, and `BioCluProcessor`. For more information about these, see [clulab Processors](https://github.com/clulab/processors).

-  Ensure `odinson.textDir` and `odinson.docDir` are set as intended.  Text will be read from `odinson.textDir`, annotated, and serialized to `odinson.docDir`.  **NOTE**: If you are using the typical directory structure (where you will have a data folder with subdirs `text`, `docs`, and `index`), you can simply specify `odinson.dataDir = path/to/your/dataDir`, and the subfolders will be handled.

### Command

```
sbt "extra/runMain ai.lum.odinson.extra.AnnotateText"
```

## Indexing annotated documents
Once you have annotated Documents, you can create an Odinson index, the datastructure that Odinson operates over.

### Configuration
Once again, you will specify the configurations in `extra/src/main/resources/application.conf`.

- Ensure the `odinson.docDir` directory contains annotated `ai.lum.odinson.Document`s (`.json` or `.json.gz`).
- Ensure `odinson.indexDir` is pointing to where you want to create the index.
- Note again, if you are using the typical directory structure (see above), you can simply ensure that `odinson.dataDir = path/to/your/dataDir` and the other paths will be correct.

### Command

```
sbt "extra/runMain ai.lum.odinson.extra.IndexDocuments"
```

## Querying using the shell
Once you have an index made, you can query it in the shell.

### Configuration
In `extra/src/main/resources/application.conf`:
- Ensure `odinson.indexDir` is correct (The odinson index will be read from this location), or if using the directory structure, make sure that `odinson.dataDir` is set to your directory containing the docs, text, and index.

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
