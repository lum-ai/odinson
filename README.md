[![Build Status](https://travis-ci.org/lum-ai/odinson.svg?branch=master)](https://travis-ci.org/lum-ai/odinson)

# Odinson

Odinson can be used to rapidly query a natural language knowledge base and extract structured relations. Query patterns can be designed over (a) surface (e.g. #1), syntax (e.g., #2), or a combination of both (e.g., #3-5). These examples were executed over a collection of 8,479 scientific papers, corresponding to 1,105,737 sentences. Please note that the rapidity of the execution allows a user to dynamically develop these queries in _real-time_, immediately receiving feedback on the coverage and precision of the patterns at scale. Please see our [forthcoming LREC 2020 paper](https://lum.ai/docs/odinson.pdf) for technical details and evaluation.

## Project overview

Odinson supports several features:

- Patterns over tokens, including boolean patterns over token features like lemma, POS tags, NER tags, chunk tags, etc
- Patterns over syntax by matching paths in a dependency graph. Note that this actually agnostic to the tags in the graph edges and it could be repurposed for matching over semantic roles or something else.
- Named captures for extracting the different entities involved in a relation

And there are many more on the way:

- Testing framework and extensive test suite
- Better error messages
- Better support for greedy and lazy quantifiers
- Lookaround assertions
- Support for an internal state to hold intermediate mentions, allowing for the application of patterns in a cascade
- Support for grammars (similar to odin)
- Filtering results by document metadata (e.g., authors, publication date)

We would also love to hear any questions, requests, or suggestions you may have.

It consists of several subprojects:

- **core**: the core odinson library
- **extra**: these are a few apps that we need but don't really belong in core,
        for example, licensing issues
- **backend**: this is a REST API for odinson
- **ui**: this is a webapp that we are building to interact with the system and visualize results to enable rapid development

The three apps in extra are:

- **AnnotateText**: parses text documents using processors
- **IndexDocuments**: reads the parsed documents and builds an odinson index
- **Shell**: this is a shell where you can execute queries (we will replace this with the webapp soon)

## Docker

To build docker images locally, run the following command via [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html):

```bash
sbt dockerize
```
We also publish images to [dockerhub](https://hub.docker.com/orgs/lumai/repositories) (see below for information on our docker images).

#### Docker image for annotating text and indexing Odinson JSON documents

```bash
docker pull lumai/odinson-extras:latest
```

See [our repository](https://hub.docker.com/r/lumai/odinson-extras) for other tags.

#### Docker image for running the Odinson REST API

```bash
docker pull lumai/odinson-rest-api:latest
```

See [our repository](https://hub.docker.com/r/lumai/odinson-rest-api) for other tags.

### Annotating text

```bash
docker run \
  --name="odinson-extras" \
  -it \
  --rm \
  -e "HOME=/app" \
  -e "JAVA_OPTS=-Dodinson.extra.processorType=CluProcessor" \
  -v "/path/to/data/odinson:/app/data/odinson" \
  --entrypoint "bin/annotate-text" \
  "lumai/odinson-extras:latest"
```

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing a directory called `text` containing the `.txt` files you want to annotate. Compressed OdinsonDocument JSON will be written to a directory called `docs` under whatever you use for `/path/to/data/odinson`.

### Indexing documents

```bash
docker run \
  --name="odinson-extras" \
  -it \
  --rm \
  -e "HOME=/app" \
  -v "/path/to/data/odinson:/app/data/odinson" \
  --entrypoint "bin/index-documents" \
  "lumai/odinson-extras:latest"
```

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing `docs`. The index will be written to a directory called `index` under whatever you use for `/path/to/data/odinson`.

### Odinson shell

```bash
docker run \
  --name="odinson-extras" \
  -it \
  --rm \
  -e "HOME=/app" \
  -v "/path/to/data/odinson:/app/data/odinson" \
  --entrypoint "bin/shell" \
  "lumai/odinson-extras:latest"
```

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing `index` (created via the `IndexDocuments` runnable).


### Running the REST API

```bash
docker run \
  --name="odinson-rest-api" \
  -d \
  -it \
  --rm \
  -e "HOME=/app" \
  -p "0.0.0.0:9001:9000" \
  -v "/path/to/data/odinson:/app/data/odinson" \
  "lumai/odinson-rest-api:latest"
```

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing `docs` and `index` (created via `AnnotateText` and `IndexDocuments` runnables).

Logs can be viewed by running `docker logs -f "odinson-rest-api"`

## Examples

We have made a few example queries to show how the system works. For this we used a collection of 8,479 scientific papers (or 1,105,737 sentences). Please note that the rapidity of the execution allows a user to dynamically develop these queries in real-time, immediately receiving feedback on the coverage and precision of the patterns at scale.

### Example of a surface pattern for extracting casual relations.

This example shows odinson applying a pattern over surface features (i.e., words) to extract mentions of causal relations. Note that Odinson was able to find 3,774 sentences that match the pattern in 0.18 seconds.

![example 1](https://github.com/lum-ai/odinson/raw/master/images/image1.png "example 1")



### Example of a doubly-anchored Hearst pattern to extract hypernymy (i.e., _X_ isA _Y_)

This example shows how Odinson can also use patterns over syntax. In this case it tries to find hypernym relations. It finds 10,562 matches in 0.37 seconds.

![example 2](https://github.com/lum-ai/odinson/raw/master/images/image2.png "example 2")



### Example of how a surface pattern can be extended (using syntax patterns) to extract additional contextual information.

This example shows how surface and syntax can be combined in a single pattern. This pattern finds 12 sentences that match in our corpus of 1,105,737 sentences. It does this in 0.01 seconds.

![example 3](https://github.com/lum-ai/odinson/raw/master/images/image3.png "example 3")



### Example of a causal pattern written over dependency syntax with a lexical trigger (i.e., _cause_).

This example shows how we can match over different aspects of tokens, lemmas in this example. Note that the ability to utilize syntax helps with the precision of the extractions (as compared with the overly simple surface rule above). Odinson finds 5,489 matches in 0.18 seconds.

![example 4](https://github.com/lum-ai/odinson/raw/master/images/image4.png "example 4")



### Example of how more complex patterns can be developed, for example, to extract the polarity of a causal influence and a context in which it applies.

This is an example of a slightly more complex pattern. Odinson is able to apply it over our corpus and finds 228 matches in 0.04 seconds.

![example 5](https://github.com/lum-ai/odinson/raw/master/images/image5.png "example 5")

## Web UI

We are also working on a web interface that will simplify debugging by displaying more information than the shell. This interface will allow us to display syntactic information when needed. We would also like to be able to interact with it to correct extractions or bootstrap patterns.

![example 6](https://github.com/lum-ai/odinson/raw/master/images/image6.png "example 6")
