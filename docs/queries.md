---  
title: Queries
has_children: true  
nav_order: 6
---  

# Odinson queries

Odinson supports two main types of query patterns, **basic** queries and **event** queries.  Each type of query can hop between surface representations and graph traversals (e.g., syntactic dependencies) using a combination of [token constraints](token_constraints.html) and [graph traversals](graph_traversals.html).

A [**basic query**](http://gh.lum.ai/odinson/basic_queries.html) minimally contains a token pattern (one or more token constraints), but optionally can also include graph traversals and additional token patterns.  Example:

    girl >nmod_from Ipanema


An [**event query**](event_queries.html) requires a `trigger`, a token pattern that indicates a possible match, and can have arguments, i.e., patterns _anchored on_ the trigger.  A simple example of this would be having a certain verb as a trigger (e.g., "cause"), and looking for the subject and object of the verb to serve as the agent and the theme of the event.

In addition to the two types of queries above, aimed at matching patterns within the body of a document, Odinson supports [**parent queries**](parent_queries.html), which filter the document collection based on information in the document metadata before an odinson query executes. Parent queries use [**Lucene query syntax**](https://lucene.apache.org/core/2_9_4/queryparsersyntax.html).
