---  
title: Parent Queries
parent: Queries
has_children: false
nav_order: 5
---  

# Parent queries

Parent queries can be applied to document metadata provided as a json of token fields, e.g.:

```
"metadata": [
  {
    "$type": "ai.lum.odinson.TokensField",
    "name": "show",
    "tokens": ["Twin", "Peaks"]
  },
  {
    "$type": "ai.lum.odinson.TokensField",
    "name": "actor",
    "tokens": ["Kyle", "MacLachlan"]
  },
  {
    "$type": "ai.lum.odinson.TokensField",
    "name": "character",
    "tokens": ["Special", "Agent", "Dale", "Cooper"]
  }
]
```

Parent queries use [**Lucene query syntax**](https://lucene.apache.org/core/2_9_4/queryparsersyntax.html) to query metadata fields. For instance, the parent query below will limit the document collection to only the documents related to the show Twin Peaks based on the example metadata above:

    show: "Twin Peaks"

The following parent query will limit the documents based on both the show and the actor:

    character: "Special Agent Dale Cooper" AND show: "Fire Walk With Me"

See the [testing suite](https://github.com/lum-ai/odinson/tree/master/core/src/test/scala/ai/lum/odinson/foundations/TestOdinsonParentQuery.scala) for more query examples. Parent queries are also available in the [REST API](https://github.com/clu-ling/odinson-example).
