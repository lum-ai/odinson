---  
title: REST API
has_children: false 
nav_order: 6
---  

# Running the REST API
We can run the REST API by starting a Docker container or launching the backend server via the ```sbt``` tool.
## 1. Using Docker
```bash
docker run \
  --name="odinson-rest-api" \
  -it \
  --rm \
  -e "HOME=/app" \
  -p "0.0.0.0:9001:9000" \
  -v "/path/to/data/odinson:/app/data/odinson" \
  "lumai/odinson-rest-api:latest"
```

After starting the service, open your browser to [localhost:9001](localhost:9001/api).

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing `docs` and `index` (created via `AnnotateText` and `IndexDocuments` runnables).

Logs can be viewed by running `docker logs -f "odinson-rest-api"`

## 2. Launching the Backend Server
The backend REST-API server is an independent application located in the "backend" folder. To launch the server, run the following command in the root folder:
```
sbt backend/run
```
Make sure to define the ```odinson.dataDir``` in the backend's configuration file (```backend/conf/application.conf```).

The main advantage of launching the backend server over a prepackaged Docker image is that it gives us more direct control over the server's configuration, which is particularly useful when a custom annotation pipeline is used and the indexed field names are different from the defaults (e.g. ```raw```, ```lemma```, ```tag```).
