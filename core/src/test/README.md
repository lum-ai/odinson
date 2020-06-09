# Odinson Core Tests

## Files

### Resources

patternsThatMatch.tsv

### Tests

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
