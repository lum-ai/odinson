---  
title: Strings
parent: Queries
has_children: false 
nav_order: 5
---  

## String rules and special characters

There are three places where Odinson needs to match a string:
- when matching the surface content of a token 
- name of the token field
- dep graph match the edge name
- surface pattern (bc equiv to token)

In all those cases, you need to use single or double quotes (they are equivalent in Odinson).  Inside the quotes, the rules for string escaping are applied (i.e., `\n` will be a newline, though you wouldn't normally encounter a newline in a token).  

If the string is a valid java identifier, then you don't need to quote it.
A valid java identifier begins with a letter or underscore and is followed by zero or more letters, digits, or underscores.  If your string is of this format, it does **not** need to be quoted.

Additionally, if you're matching anything other than the default token (i.e., if you're using the `[...]` or `>...` notation), then we add two additional options to that list, `:` and `-`.  That is, `[chunk=B-NP]` does not need any quotes, and neither does `>nmod:poss`. (You can quote it, knock yourself out!)

However, these rules do not apply to the default word in surface pattens, so  you do need quotes in this pattern: 
    
    "3:10" to Yuma

Further, you can also use regular expressions anywhere Odinson supports strings.  For example, you can specify `>/nmod_.*/`.
