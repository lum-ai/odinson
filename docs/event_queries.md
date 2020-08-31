---  
title: Event Patterns
parent: Queries
has_children: false 
nav_order: 4
---  

# Event queries

Odinson also supports **event queries**, which are look for a trigger, and then one or more required (or optional) arguments.
These arguments are defined in terms of their connection to the found trigger.

As an example, consider this simple event rule that finds the subject and object of the verb _cause_:

      - name: example-event-rule
        label: Causal
        type: event
        priority: 1
        pattern: |
          trigger = causes
          subject = >nsubj []
          object = >dobj [] 

The result of applying this rule to a sentence such as "Rain causes puddles" is a Mention, which has a trigger (_causes_), and two arguments: a `subject` (_Rainfall_), and an `object` (_puddles_).
Since we did not specify any labels for the _type_ of the subject and object arguments, they will have the same label as the overall event (`Causal`).

## Priorities

One element of an event query is the `priority`.  This is the order in which the rule(s) should be applied.  This is useful when you wish to rely on the output of a previous rule in another rule.
For example, in these two rules, we first find `Person` Mentions, and then extract `Construction` events:

      - name: person-rule
        label: Person
        type: basic
        priority: 1
        pattern: |
           [Hamilton]

      - name: construction-rule
        label: Construction
        type: event
        priority: 2
        pattern: |
          trigger = [lemma=build]
          subject: Person = >nsubj [] 

Here, the engine will first apply `person-rule`, then `construction-rule`, due to the order indicated with the priorities.
By adding the type specification to the `subject` argument (`subject: Person`), we indicate that the subject of the trigger _must_ be a previously found `Person`.
However, if we instead want to allow the rule to match _even if_ the found subject is _not_ a `Person`, we can make use of **promotion**.


## Argument Promotion

Argument promotion allows us to extract nested events in the moment, without requiring them to have been previously found. 
This feature must be enabled, using the `^`, as shown here:

      - name: construction-rule
        label: Construction
        type: event
        priority: 2
        pattern: |
          trigger = [lemma=build]
          subject: ^Person = >nsubj []

Here, if the subject was not already found as a `Person`, we will _still_ extract the event, and we will assign the label `Person` to the found subject.

## Optional and Required Arguments

Event arguments can either be optional or required, where required is the default.  An optional argument will be extracted if present, but will not prevent the event from succeeding if not.  Required arguments, on the other hand, will cause the event query to fail if they are not found.
Optional arguments are indicated with a `?`, as with the second argument below.

      - name: construction-rule
        label: Construction
        type: event
        priority: 2
        pattern: |
          trigger = [lemma=build]
          subject: Person = >nsubj []
          structure: Building? = >dobj []

## Quantifying arguments

Arguments can be quantified, using the quantifiers described [here](quantifiers.html).
For example:

      - name: lots-of-quantifiers
        label: Eating
        type: event
        priority: 2
        pattern: |
          trigger = [eats]
          subject: Person{1,2} = >nsubj []
          food: ^Dessert+ = >dobj
          tool: ^Utensil* = >nmod_with

In order to succeed, this query must find an instance of the word `eats` with one or 2 `Person` mentions as the subject(s), and one or more `Dessert` mentions as food arguments.  If available, it will also match 0 or more `Utensil` mentions as tool arguments.  
 

