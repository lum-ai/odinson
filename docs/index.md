---
title: Overview
has_children: false
nav_order: 1
---

# Overview

This repository contains the code for Odinson, a powerful and highly optimized  
open-source framework for information extraction.  
Odinson is a rule-based information extraction framework, which couples a 
simple yet powerful pattern language that can operate over multiple 
representations of text, with a runtime system that operates in near 
real time. 

In the Odinson query language, a single pattern may combine regular 
expressions over surface tokens with regular expressions over graphs 
such as (but not limited to) syntactic dependencies. 
To guarantee the rapid matching of these patterns, the framework 
indexes most of the necessary information for matching patterns, 
including directed graphs such as syntactic dependencies, into a custom 
Lucene index. Indexing minimizes the amount of expensive pattern matching 
that must take place at runtime. 

Odinson is designed to facilitate real-time (or near real-time) queries over 
surface, syntax, or both.
The syntax is based on that of its predecessor language, Odin, but there are some 
key divergences, detailed here.



## Authors
[Marco Valenzuela-Escárcega](https://github.com/marcovzla), 
[Gustave Hahn-Powell](https://github.com/myedibleenso), Dane Bell, Becky Sharp, 
[Keith Alcock](http://www.keithalcock.com), George Barbosa, and 
[Mihai Surdeanu](http://surdeanu.info/mihai/)

## License
Our code is licensed by subproject as follows:
+ **`core`** - Apache License Version 2.0. Please note that the `core` subproject 
does not interact with the `extra` subproject below.
+ **`extra`** - GLP Version 3 or higher, due to the dependency on 
[Stanford's CoreNLP](http://stanfordnlp.github.io/CoreNLP/). 
If you use only `CluProcessor`, this dependency does not have to be included 
in your project.

## Citation

If you use Odinson, please cite the following:
Valenzuela-Escárcega, M. A., Hahn-Powell, G., & Bell, D. (2020, May). 
Odinson: A fast rule-based information extraction framework. In Proceedings of The 
12th Language Resources and Evaluation Conference (pp. 2183-2191).
 [[pdf]](https://www.aclweb.org/anthology/2020.lrec-1.267.pdf) 
 [[bib]]()

**TODO** bib

