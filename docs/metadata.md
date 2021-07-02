---  
title: Metadata Query Language
parent: Queries
has_children: false 
nav_order: 7
---  

# Metadata Query Language

Sometimes when we perform queries in Odinson we are only interested in a subset of the results.
Maybe we are only interested in extractions from documents written by a particular author, or published in a specific venue, or maybe we only care about documents published recently.
Odinson can index metadata associated to each document, which can be used to filter the results of a query through a metadata filter query.

Odinson supports two main types of metadata: **numeric** and **textual**.

### Numeric Metadata
Numeric metadata can be compared using the common comparison operators used in most programming languages: equals (`==`), not equals (`!=`), less than (`<`), greater than (`>`), less than or equals (`<=`), and greater than or equals (`>=`).
One of the elements being compared must be an indexed _metadata field_, and the other must be a _numeric value_, e.g., `citations > 5`.
These comparisons can be chained together, allowing us to express ranges in a more concise way, e.g., `1 < citations < 10`.

### Textual Metadata
From those comparison operators, textual metadata only supports the equals (`==`) and not equals (`!=`) operators. Equals checks for exact matching, e.g., `publisher == 'mit press'`.
Note that the textual content is wrapped in quotation marks.

Sometimes exact textual matching can be too stringent, and a containment check can be more appropriate.
This can be accomplished with the `contains` operator, e.g., `venue contains 'language'`.
To specify that a metadata field should _not_ contain a given text, you can use: `venue not contains 'language'`

To make textual comparison more robust, we perform some normalization of the textual metadata fields.
 - unicode-aware case folding
 - NFKC unicode normalization
 - some unicode characters are transformed into ASCII equivalents (e.g., arrows, ligatures, etc.)
 - removal of diacritics

### Combining Filters 
The metadata query language supports the and (`&&`), or (`||`), and not (`!`) operators to combine individual field constraints into a more complex filter, e.g., `1 < citations < 10 && venue contains 'language'`.

### Dates
The metadata query language offers special support for dates using the `date` function, which returns a numeric representation of the date.
As such, you can write queries against dates as you would with other numeric metadata.
For example, `date(2020, 'Jan', 1) < pub_date <= date(2020, 'Dec', 31)` would return documents published in 2020.
This provides a lot of flexibility when checking for specific dates, but since checking for years is a common use case, we provide a shortcut.
The query presented above can also be expressed as `pub_date.year == 2020`.
Months can be expressed as their full names, common abbreviations, or number (i.e., "August", "Aug", or 8).

### Nested Fields
Another capability of the metadata query language is its support for nested metadata fields.
For example, authors are usually indexed as nested fields so that their first and last name are associated with each other, and _not_ with other authors.
To query nested fields, we need to specify the name of the field and the query to be performed on it.
For example, if we had a document with two authors named _Jane Smith_ and _John Doe_, then we could match this document with the queries `author{first=='jane' && last=='smith'}` or `author{first=='john' && last =='doe'}`, but not `author{first=='jane' && last=='doe'}`.
Not all attributes of a nested field must be specified.  For example, given the authors above, this would also match: `author{first=='jane'}`

### Regular Expressions
The metadata query language also supports Lucene regular expressions, such as:
- `author{first=='/j.*/' && last=='/d.*/'}`
- `keywords contains '/bio.*/'`
