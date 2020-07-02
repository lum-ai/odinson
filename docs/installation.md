---
title: Getting Started
has_children: true
nav_order: 3
---

# Setup

This software has been tested with Java 1.8 and Scala 2.12.10, and is available through sbt and Maven central.


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

Or, if you're using Maven, add this to your `pom.xml` file:

```xml
<dependency>
   <groupId>ai.lum</groupId>
   <artifactId>odinson-core_2.12</artifactId>
   <version>x.x.x</version>
</dependency>
```


## How to compile

As this is a sbt project, you can compile the code with `sbt compile`, then you can run any of the main files with either `sbt core/run` or `sbt extra/run`, depending on the location of the desired runnable.  

## Example usage

For an example usage, please see the complete working example [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/scala/ai/lum/odinson/extra/Example.scala).

To run it from the command line, use:

    sbt extra/run
     
and choose `Example` off the list.

To use it, you will need to point to an Odinson index by specifying the correct path in `application.conf`. If you need help making an index, or setting up your config file, there is info [here](https://github.com/lum-ai/odinson/tree/master/extra).

The file containing the Odinson rules being used in this example is [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/resources/example/rules.yml), and the output is a json lines file, with one line per extraction.  Please note that this example is meant to be illustrative only, and the output produced is not a true serialization of the extracted Mentions (i.e., only some attributes are included in the output). 
