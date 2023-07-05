# String rules and special characters

There are four places where Odinson needs to match a string:
1. for the name of the token field (e.g., `tag` or `lemma`)
2. when matching the surface content of a token 
3. when matching the label of a graph edge (e.g., syntactic dependency label)
4. when patching a plain surface pattern (e.g., `dog` when not wrapped in square brackets, as this is a special case of 2, above)

In all those cases, you need to use single or double quotes (they are equivalent in Odinson).  Inside the quotes, the rules for string escaping are applied (i.e., `\n` will be a newline, though you wouldn't encounter a newline in a token under normal circumstances).  

**However, if the string is a valid java identifier, then you don't need to quote it.**  A valid java identifier begins with a letter or underscore and is followed by zero or more letters, digits, or underscores.  If your string is of this format, it does **not** need to be quoted.

Additionally, if you're matching anything other than the default token (i.e., if you're using the `[...]` or `>...` notation), then we add two additional options to that list, `:` and `-`.  That is, `[chunk=B-NP]` does **not** need any quotes, and neither does `>nmod:poss`. (You can quote it if you want, knock yourself out!)

As mentioned above, these rules do not apply to the default word in surface pattens, so you **do** need quotes in this pattern: 
    
    "3:10" to Yuma

Further, you can also use **regular expressions** anywhere Odinson supports strings.  For example, you can specify `>/nmod_.*/`.
