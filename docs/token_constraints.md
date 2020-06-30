---  
title: Token Constraints
parent: Queries
has_children: false 
nav_order: 1
---  

The simplest possible Odinson patterns consist of a single token constraint. A
token constraint specifies what must be true of a token in order for it to
be a valid extraction.  These constraints are limited only by what you include
in your index.  For example, we commonly include part of speech tag, named entity 
information (NER), and chunk, but any token-level tag you include in the Document
and index will be available to you!

## Example

If you write a query such as this:

    dog

Odinson will look for any occurrence of the word `dog`.  Unless specified 
otherwise, this will be *case-insensitive* and will normalize accents and unicode 
characters.  That is, this pattern will match: `dog`, `DoG`, and `dög`.

## Using the token fields

If you want to write a token constraint that uses the indexed fields, you can use
this:

    [tag=/N.*/]
    
This pattern will match any token in a document whose part of speech tag begins
with "N".  Here, `tag` is the specified field of the constraint.  Any token constraint
with an unspecified field (e.g., `[dog]` which is equivalent to the above example)
will be matched against the `norm` field, which is the default.  
The field names are specified [here](https://github.com/lum-ai/odinson/blob/master/core/src/main/resources/reference.conf), and
for most use cases should not need to be modified.

## Operators and quantifiers for token constraints

You can combine and nest constraints for a token 
using operators, `&` (for AND) and `|` (for OR) and parentheses.
For example, the following would match a word whose tag starts with "N" and
which is also tagged as an organization using NER or is a proper noun.

    [tag=/N.*/ & (entity="ORGANIZATION" | tag="NNP")]
    
Token constraints can be used with the quantifiers (`*`, `+`, `?`, and ranges), for 
example, to match one or more nouns in a row, you can specify:

    [tag=/N.*/]+
    
For more information, please see the `Quantifiers` page at the left.
