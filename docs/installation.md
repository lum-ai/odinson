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

