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
We also publish images to [dockerhub](https://hub.docker.com/orgs/lumai/repositories) (see below for information on our docker images).

### Docker image for annotating text and indexing Odinson JSON documents

```bash
docker pull lumai/odinson-extras:latest
```

See [our repository](https://hub.docker.com/r/lumai/odinson-extras) for other tags.

### Docker image for running the Odinson REST API

```bash
docker pull lumai/odinson-rest-api:latest
```

See [our repository](https://hub.docker.com/r/lumai/odinson-rest-api) for other tags.