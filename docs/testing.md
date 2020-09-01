---  
title: Testing
has_children: false
nav_order: 11
---  

# How does Odinson testing work?

Odinson uses [ScalaTest](https://www.scalatest.org/) 3 as a testing solution,
and [Scoverage](https://github.com/scoverage/sbt-scoverage) for coverage checking.

To run the entire collection of Odinson tests, run:

```
sbt test
```
You can use `sbt 'runOnly *NameOfTheClass'` to run a single test.

## Testing structure

### Core tests

Core tests are divided into 6 categories:

1. events: for event rules
2. foundations: for fundamental aspects of the Odinson system, e.g., the `ExtractorEngine`
3. patterns: tests different matching patterns
4. serialization
5. traversals: for traversing graph fields
5. util: utility methods

When writing a new test,
try a category that best fits it.

### Documents for testing

When testing rules or components,
you will find yourself needed a testing sentence
to use with one of your tests.
Inside `ai.lum.odinson.documentation.ExampleSentences` you will find a collection of sentences
written for previous tests.
You can reuse those sentences if you want.
If you need a visualization of an odinson sentence you can use
[OdinsonDocEditor](https://odinson-doc-editor.herokuapp.com/).

## How to add a unit test

Assuming you are creating a new file,
make sure you follow these guidelines:

- Your test file should have a unique name and start with `Test` followed by the rest of the name, camel-cased.
- Follow the [scalatest documentation](https://www.scalatest.org/) when writing tests.
- Aim to test a single functionality in each test.
- Be mindful when naming tests and only use `it` when necessary.
- Every test suite should extend `ai.lum.odinson.BaseSpec`.
- It is good practice to structure your tests to avoid unhelpful messages.  For example, if
you consistently assert that `x should be (true)`, when a test fails you will only know that "false did not equal true", instead of the real problem!

## Code coverage

We use [Codecov](https://codecov.io/) as our code coverage solution.
The code coverage is calculated whenever you open a PR to the `master` repository.

You can check the code coverage locally running:

```
sbt coverage test
sbt CoverageReport
```

## Test example

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
