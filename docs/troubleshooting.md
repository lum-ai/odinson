---  
title: Troubleshooting
has_children: false
nav_order: 12
---  


# Index Not Found Exception / white.lock error while using a docker image

Error message:

 >  Error in custom provider, org.apache.lucene.index.IndexNotFoundException: no segments* file found in MMapDirectory@/app/data/odinson/index lockFactory=org.apache.lucene.store.NativeFSLockFactory@68e2d03e: files: [write.lock]

Run the following command and index the documents using a docker image as described [here](docker.md)

```bash
rm -rf /path/to/data/odinson/index/
```

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing `index`.


# No such method error while using a docker image

Error message:

> 'void sun.misc.Unsafe.putInt(java.lang.Object, int, int)'

Make sure you have java 11 installed (e.g., OpenJDK 11).

# Access Denied Exception while indexing with a docker image

Error message:

> Exception in thread "main" java.nio.file.AccessDeniedException: /app/data/odinson/index/write.lock


```bash
chmod -R 777 /path/to/data/odinson
```

**NOTE**: Replace `/path/to/data/odinson` with the path to the directory containing `docs`. You may need to use `sudo` at the beginning of this command.


# No repo found while running offline documentation with a docker image

Error message:

> Liquid Exception: No repo name found. Specify using PAGES_REPO_NWO environment variables, 'repository' in your configuration, or set up an 'origin' git remote pointing to your github.com repository. in `/_layouts/default.html`


Add the following line in the `docs/_config.yml`:

```bash
repository:         'lum-ai/odinson.git'
```

# `/favicon.ico' not found while running offline documentation via docker

When running locally, this error can likely be ignored as explained [here](https://talk.jekyllrb.com/t/favicon-ico-not-found/2571/5)
