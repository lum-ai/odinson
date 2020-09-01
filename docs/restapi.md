---  
title: REST API
has_children: false 
nav_order: 6
---  

# Running the REST API

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
