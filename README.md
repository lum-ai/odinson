![Tests (GitHub Actions)](https://github.com/lum-ai/odinson/workflows/Odinson%20CI/badge.svg)
[![codecov](https://codecov.io/gh/lum-ai/odinson/branch/master/graph/badge.svg)](https://codecov.io/gh/lum-ai/odinson)

# Odinson

Odinson can be used to rapidly query a natural language knowledge base and extract structured relations. Query patterns can be designed over (a) surface, syntax, or a combination of both.
In particular, Odinson has been highly optimized to deliver these results in near real-time, which enables users to dynamically develop queries, receiving immediate feedback on the coverage and precision of the patterns at scale. 
Please see our [LREC 2020 paper](https://lum.ai/docs/odinson.pdf) for technical details and evaluation.

## Documentation
Please see [http://gh.lum.ai/odinson/](http://gh.lum.ai/odinson/), for documentation, including information about installation, capabilities, and learning how to build queries.

## Project overview

Odinson supports a wide range of features, including:

- Patterns over tokens, including boolean patterns over token features like lemma, POS tags, NER tags, chunk tags, etc
- Patterns over syntax by matching paths in a dependency graph. Note that this actually agnostic to the tags in the graph edges and it could be repurposed for matching over semantic roles or something else.
- Named captures for extracting the different entities involved in a relation
- Support for greedy and lazy quantifiers, lookaround assertions, etc.
- Support for an internal state to hold intermediate mentions, allowing for the application of patterns in a cascade
- Support for grammars
- Filtering results by document metadata (e.g., authors, publication date)
- and much, much more!

Again, please see [our documentation](http://gh.lum.ai/odinson/), for more information! 

We would also love to hear any questions, requests, or suggestions you may have.




