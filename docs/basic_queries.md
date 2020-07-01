---  
title: Basic Patterns
parent: Queries
has_children: false 
nav_order: 2  
---  

## Basic queries

Odinson basic queries allow for specifying a condition for the start of a "path", a valid end of the path,  and the traversals that are licensed for the path itself. These conditions can be specified in terms of _token constraints_ (surface patterns),  _path constraints_ (graph traversals), or both.

### Surface pattens

An example of a surface pattern is shown here:

    [tag=/N.*/] and [lemma=dog]
    
This pattern will match any occurrence in the corpus of a noun (as specified by the tag beginning with N) followed by _and_, and finally being followed immediately by a word whose lemma is _dog_. 


### Named Captures

To capture aspects of the match, we can add a _named capture_.  To do this, you need to specify the name of the capture and surround the portion of the pattern that is to be captured with `(?<name> ... )`.  For example, when this query: 

    (?<animal> [tag=/N.*/]) and [lemma=dog]
    
is applied to the sentence "I like cats and dogs", the system 
will find the mention "cats and dogs", and this mention would contain a named capture with the label `animal` containing "cats".


### Adding syntax

Basic queries can also incorporate graph traversals.  The direction that the dependency is traversed is encoded by placing a `>` (outgoing) or `<` (incoming) in front of  the dependency name.  These dependency labels support regular expression notation (i.e., `/nmod_.*/`), the full string rules are included [here]() TODO link. 

Here is an example of a pattern that captures a subject-verb-object relation involving _phosphorylation_:

    (?<controller> [entity=PROTEIN]) <nsubj phosphorylates >dobj (?<theme> [entity=PROTEIN])
    
This pattern will look for a sentence in which a token tagged as a PROTEIN (though a hypothetical NER component) is the subject of the verb "phosphorylates", and in which that same verb has a direct object which is also tagged as a PROTEIN.  To put it another way, reading the pattern from left-to-right, Odinson will look for a token tagged as a PROTEIN, try to traverse backwards against an incoming `nsubj` dependency arc, land on "phosphorylates", and then traverse an outgoing `dobj` dependency arc to land on a token also tagged as a PROTEIN.   If it finds such a sentence, the first PROTEIN will be extracted with the label `controller`, and the second will have the label `theme` (because of the named captures). 

### Combining representations

Note that in Odinson, patterns can hop between surface and syntax representations arbitrarily often, as is done in this query:

    
    
which has a successful match in the sentence: TODO


### Wildcards

Odinson supports wildcards both for token constraints and graph traversals.  To specify a wildcard for a token (i.e., _any_ token), use `[]`.  The incoming and outgoing dependency wildcards are `<<` and `>>`, respectively.  