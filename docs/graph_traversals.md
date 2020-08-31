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
 
 
## Quantifiers and Expansion

Like any other pattern component, graph traversals (as well as these wildcards) can be combined with [quantifiers](quantifiers.html), e.g., `>>{2,3}`.
Additionally, groups of graph traversals can be wrapped in parentheses and quantified.  
For example, to specify traversing an outgoing `nsubj` edge and optionally up to two `conj_and` edges, you can use:

    She saw >dobj [] (>conj_and []){,2}
    
In the sentence "She saw me and Julio," this will traverse the `dobj` and the following `conj_and` and extract `Julio`.
However, if we want to use the quantified (and here, optional) graph traversals to **expand** the mention, we can indicate it with `(?^ ... )` as follows:
   
    She saw >dobj (?^ [] >conj_and []){,2})
    
The left-hand side, `(?^`,  indicates where the expanded mention should begin, and the final `)` indicate where it should stop.
The above pattern, with the same sentence, would instead match `me and Julio`.