# Odinson Core Tests

## Files

### Resources

patternsThatMatch.tsv

### Tests

```
scala.ai.lum.odinson = [
  TestArgsQuantifiers.scala
  TestEventTriggers.scala
  TestFields.scala
  TestMoreEvents.scala
  TestPatterns.scala
  TestRuleFile.scala
  TestTraversals.scala
  TestUnicodeNormalization.scala
  TestUnsafeSerializer.scala
  TestUtils.scala
]
```

#### Description of the tests

**TestArgsQuantifiers.scala**

1. Check if the theme of a event rule returns the correct theme.

**TestEventTriggers.scala**

1. Check if a basic rule returns the correct mentions
2. Check if an event rule returns the correct mentions
3. Check if an event rule with 2 triggers where one has quantifiers returns the correct mentions
4. Same as 3, but the quantifier is at the right hand side.
5. Check if an event rule returns the correct triggers using part of speech
6. Check if [] works with event rules

**TestFields.scala**

Simple tests for the extractor engine.

**TestMoreEvents.scala**

1. tests ee.query() to find patterns
2. tests ee.query to find more than one mention
3. tests ee.query with optionals
4. tests a case where there is no match.
5. tests event with location

**testPattern.scala**

1. test a file with patterns (patternsThatMatch.tsv)

**TestRuleFile.scala**

1. test an eventrule
a. test if the aguments are corret

**TestTraversals.scala**

1. test matches that follow a dependency tree.

**TestUnicodeNormalization.scala**

1. unicode normalization
2. unicode aggressive normalization
3. normalize multi units
4. check support to casefolding
5. remove diacritics
6. character replacement

This is not testing anything from Odinson.
This is testing something from lum.common should we put these tests anywhere else?

**TestUnsafeSerializer.scala**

1. serialize and deserialize a graph and test if the serialization works

**TestUnsafeSerializer.scala**

Defines helper functions for testing.

### Types of tests found

1. Serialization
2. Event rules
3. Traversals
4. Util (Unicode for now?)
5. Patterns
6. Quantifiers

Groups that should be added?:

7. Logical Operators
8. Basic Functioning

What to do with TestPatterns.scala

## Fixtures

List of fixtures for core tests

Righ now classes are extending FlatSpec with Matchers,
but in the future I want to have a base class with the mixtures we need.



### Basic

ExtractorEngine
(Odinson)Document
