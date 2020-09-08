---  
title: Offline Documentation
has_children: false  
nav_order: 10
---  

# Requirements

In order to deploy the documentation offline you
must have `bundle` and `jekyll` gems installed first.

You can find out how to install both gems [here](https://jekyllrb.com/docs/).

# How to deploy

Navigate into the `docs` folder
and run the following command to download the dependencies:

```shell
bundle install
```

After installing the dependencies,
run Jekyll server:

```shell
bundle exec jekyll serve
```

Access `localhost:4000` on your web browser
and the docs should be rendered nice and smooth.


# Running via docker

To build the docker, run the following command from the `docs` folder:

```shell
docker build -f Dockerfile -t lumai/jekyll .
```

Generate the site using the following command:

```bash
docker run --rm \
  -v "$PWD:/srv/jekyll" \
  -it lumai/jekyll \
   jekyll build
```

For working on the documentation locally, run the following:

```bash
docker run --rm \
  -p 4000:4000 \
  -v "$PWD/docs:/srv/jekyll" \
  -it lumai/jekyll \
  jekyll serve --port 4000
```

Access `localhost:4000` on your web browser.
