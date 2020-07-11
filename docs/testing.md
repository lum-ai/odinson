---  
title: Testing
has_children: false
nav_order: 7
---  

# How does odinson testing works?

Odinson uses [ScalaTest](https://www.scalatest.org/) 3 as testing solution,
and [Scoverage](https://github.com/scoverage/sbt-scoverage) for coverage checking.

To run the entire collection of odinson tests, run:

```
sbt test
```
You can use `sbt 'runOnly *NameOfTheClass'` to run a single tests.

## Testing structure

### Core tests

Core tests are divided into 6 categories:

1. events: for event rules
2. foundations: for components the user interacts first
3. patterns: tests different matching paterns
4. serializaation
5. traversals: traversing graph fields
5. util

When writing a new test,
try a category that best fits it.

#### Documents for testing event rules

Test suites that test event rules should extend `ai.lum.odinson.events.EventSpec`.

When testing some rules,
you will find yourself wrinting a testing sentence
to use with one of your tests.
Inside `EventSpec` you will find a collection of sentences
written for previous tests.
You can reuse those sentences if you want.
If you need a visualisation of an odinson sentence you can use
[OdinsonDocEditor](https://odinson-doc-editor.herokuapp.com/).

# How to add a unit test

Assuming you are creating a new file,
make sure you follow the following rules:

- Your test file should have a unique name and start with `Test` followed by the rest of the name camel cased.
- Follow the scalatest documentation when writing tests: https://www.scalatest.org/.
- Aim to test a single function for in each test.
- Be mindiful when naming tests and only use `it` when necessary.
- Every test suite should extend `ai.lum.odinson.BaseSpec`.

# Code coverage

We use [Codecov](https://codecov.io/) as our code coverage solution.
The code coverage is calculated whenever you open a PR to the `master` repository.

You can check the code coverage locally running:

```
sbt coverage test
sbt CoverageReport
```

# Test example

```scala
// part of foundations
package ai.lum.odinson.foundations

import ai.lum.odinson.BaseSpec
import org.scalatest._
import collection.mutable.Stack

// extend BaseSpec
class TestSomething extends BaseSpec {
  // use a descriptive name
  "A Stack" should "pop values in last-in-first-out order" in {
    val stack = new Stack[Int]
    stack.push(1)
    stack.push(2)
    stack.pop() should be (2)
    stack.pop() should be (1)
  }
}
```
