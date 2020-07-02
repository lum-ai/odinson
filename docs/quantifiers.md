---  
title: Quantifiers
parent: Queries
has_children: false 
nav_order: 5
---  

# Quantifiers 

Odinson supports a full range of quantifiers which can be applied to any pattern element (i.e., tokens, graph traversals, or combinations of these).
 
 | Quantifier | Description                                        | Example     |
 |------------|----------------------------------------------------|-------------|
 | `?`        | indicates a pattern element is optional            | >amod?      |
 | `*`        | matches zero or more of an element                 | []*         |
 | `+`        | matches one or more of an element                  | (>amod [])+ |
 | `{m, n}`   | matches at least `m` and at most `n` of an element | >>{2,3}     |

Odinson also supports both greedy and lazy usage of these quantifiers.  For example, `[tag=/N.*/]+?` will perform lazy (or reluctant) matching of nouns.  

