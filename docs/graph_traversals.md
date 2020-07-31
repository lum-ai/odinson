---  
title: Graph Traversals
parent: Queries
has_children: false 
nav_order: 2
---  

# Graph traversals

Queries can also incorporate graph traversals, most commonly used for traversing the dependency syntax graph.  

A graph traversal is encoded with two parts:
 - a **direction**, and 
 - a **label**

The direction that the edge (e.g., dependency) is traversed is encoded by placing a `>` (outgoing) or `<` (incoming) in front of the label.  

The edge/dependency labels, as strings, support regular expression notation (i.e., `/nmod_.*/`).  The full string rules are provided [here](strings.html).

So, for example, to traverse an incoming `nsubj` edge, you'd use: 

    <nsubj 
    
and to traverse an outgoing `dobj` or `xcomp` you'd use:

    >/dobj|xcomp/
    
## Wildcards

Odinson supports wildcards for graph traversals.  They are:
 - `<<` : any incoming edge
 - `>>` : any outgoing edge
 
 
## Quantifiers 

Like any other pattern component, graph traversals (as well as these wildcards) can be combined with [quantifiers](quantifiers.html), e.g., `>>{2,3}`.   