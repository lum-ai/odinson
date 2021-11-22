---  
title: Docker
parent: Getting Started
has_children: false
nav_order: 2  
---  

# Using with Docker

To build docker images locally, run the following command via [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html):

```bash
sbt dockerize
```
**NOTE**: this depends on having OpenJDK 11 installed on your machine.

We also publish images to [dockerhub](https://hub.docker.com/orgs/lumai/repositories) (see below for information on our docker images).

### Docker image for annotating text and indexing Odinson JSON documents

```bash
docker pull lumai/odinson-extras:latest
```

See [our repository](https://hub.docker.com/r/lumai/odinson-extras) for other tags.

### Annotating text using the docker image

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

### Indexing documents using the docker image

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