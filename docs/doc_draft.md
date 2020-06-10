# Draft of the Odinson manual content

## Description
Odinson is a highly optimized information extraction framework designed to facilitate
real-time (or near real-time) queries over surface, syntax, or both.
The syntax is based on that of its predecessor language, Odin, but there are some 
key divergences.
Below we explain how to build queries, from simple to more complex.

## Prerequisites

 - build an index
 - what entry point are we pointing people to?
 
## Meta-data available for querying...
Odinson is largely agnostic to the tools used to syntactically preprocess 
the text, as long as [TODO any requirements?].
For all of the examples contained here, we assume the documents in the 
odinson index have been processed with (a) part of speech tags, (b) lemmas, 
(c) named entities, and (d) universal dependencies.

TODO: links/refs for the above.
 
## Basic queries

### Starting with surface

Odinson basic queries allow for specifying a condition for the start of a "path", 
a valid end of the path,  and the traversals that are licensed for the path itself.
These conditions can be specified in terms of _token constraints_ and 
_path constraints_.
For example, in this query:

    [tag=/N.*/] and [lemma=dog]
    
in order to have a match, there must be a sentence with a 
noun (as specified by the tag beginning with N) followed by _and_, 
and finally being followed immediately by a word whose lemma is _dog_. 
If we want to capture aspects of the match, we can add a _named capture_ as is done here:

    (? <animal> [tag=/N.*/]) and [lemma=dog]
    
When this query is applied to a the sentence "I like cats and dogs.", the system finds 
a match with a named capture having the label "animal" and the content "cats."

### Adding syntax

If we want to write a path that involves a syntactic traversal, that can be 
incorporated directly into the basic query.  For example, if we want to capture a 
subject-verb-object relation that involves phosphorylation, we can specify a 
pattern such as:

    (? <controller> [entity=PROTEIN]) <nsubj phosphorylates >dobj (? <theme> [entity=PROTEIN])
    
Here, the pattern will look for a sentence in which a token tagged as a 
PROTEIN (though a hypothetical NER component) is the subject of the verb 
"phosphorylates", and in which that same verb has a direct object which is also
tagged as a PROTEIN.  If it find such a sentence, the first PROTEIN will be extracted 
with the label "controller" and the second will have the label "theme."

Note that in Odinson, we can hop between surface and syntax representations arbitrarily often, 
as is done in this query:

    In the middle >nmod_of [] I go >xcomp walking >nmod_in [] from the mountains >nmod_to []
    
which has a successful match in the sentence: 
"In the middle of the night I go walking in my sleep from the mountains 
of faith to the river so deep."
[TODO: make sure it does]

Of note in above query is the `[]`, which is a wildcard for a token constraint, 
basically indicating "any token."  The corresponding wildcards for incoming and 
outgoing dependencies are `<<` and `>>`, respectively.
## Event queries

## Parent queries