---
title: Installation
has_children: false
nav_order: 2
---

# Setup

This software requires Java 1.8 and Scala 2.XX (todo) or higher.

This software is available through sbt and Maven central.


To include into an existing sbt project, add this to your `build.sbt` file:

```scala
libraryDependencies ++= {                         
  val odinsonVer = "X.X.X" 
  Seq(
      "ai.lum"        %% "odinson-core"             % odinsonVer,
      "ai.lum"        %% "odinson-extra"            % odinsonVer
  )     
}
```

- TODO: using with Maven instructions

- TODO: web/rest api

-- TODO: Docker

## How to compile

As this is an abt project, you can compile the code with `sbt compile`, 
then you can run any of the main files with either `sbt core/run` 
or `sbt extra/run`, depending on the location of the desired runnable.  

## Example usage

For an example usage, please see the complete working 
example [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/scala/ai/lum/odinson/extra/Example.scala).

To run it from the command line, use:

    sbt extra/run
     
and choose `Example` off the list.

The file of Odinson rules being used in this example is [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/resources/example/rules.yml).

and it will generate a json lines file. You need to point it at an index in application.conf, if you need help making an index, there is info here: https://github.com/lum-ai/odinson/tree/master/extra

Please let me know if this helps!