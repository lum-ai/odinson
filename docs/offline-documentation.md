---  
title: Offline Documentation
has_children: false  
nav_order: 6  
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
