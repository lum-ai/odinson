---  
title: Overview  
has_children: false  
nav_order: 1  
---  
  
# Overview  
  
This repository contains the code for Odinson, a powerful and highly optimized open-source framework for information extraction.  Odinson is a rule-based information extraction framework, which couples a simple, yet powerful pattern language that can operate over multiple representations of text, with a runtime system that operates in near real time.   
  
In the Odinson query language, a single pattern may combine regular expressions over surface tokens with regular expressions over graphs such as (but not limited to) syntactic dependencies.   

To guarantee the rapid matching of these patterns, the framework indexes most of the necessary information for matching patterns, including directed graphs such as syntactic dependencies, into a custom Lucene index. Indexing minimizes the amount of expensive pattern matching that must take place at runtime.   
  
Odinson is designed to facilitate real-time (or near real-time) queries over surface, syntax, or both.  The syntax is based on that of its predecessor language, Odin, but there are some key divergences, detailed here.  
  
## Project Structure

Odinson consists of the following subprojects:

- **core**: the core odinson library
- **extra**: these are a few apps that we need but don't really belong in `core`, due to things like licensing issues
    
In [`c4815a8`](https://github.com/lum-ai/odinson/pull/357), the Odinson REST API was moved to a separate repository. The project is being partially rewritten before it is publicly released.

## License  

Odinson is Apache License Version 2.0. 

There are some supplemental utilities in the `extra` subproject that depend on GPL, notably [Stanford's CoreNLP](http://stanfordnlp.github.io/CoreNLP/).   
  
## Citation  
  
If you use Odinson, please cite the following:  

   Valenzuela-Escárcega, M. A., Hahn-Powell, G., & Bell, D. (2020, May).  Odinson: A fast rule-based information extraction framework. In Proceedings of The 12th Language Resources and Evaluation Conference (pp. 2183-2191).   [[pdf]](https://www.aclweb.org/anthology/2020.lrec-1.267.pdf)
       
Bibtex:       

{% raw %} 
```
@InProceedings{Valenzuela:2020,
  author    = {Valenzuela-Escárcega, Marco A. and Hahn-Powell, Gus and Bell, Dane},
  title     = {{O}dinson: {A} fast rule-based information extraction framework},
  booktitle      = {{Proceedings of the 12th Language Resources and Evaluation Conference}},
  month          = {May},
  year           = {2020},
  address        = {Marseille, France},
  publisher      = {European Language Resources Association},
  pages     = {2183--2191},
  abstract  = {We present Odinson, a rule-based information extraction framework, which couples a simple yet powerful pattern language that can operate over multiple representations of text, with a runtime system that operates in near real time. In the Odinson query language, a single pattern may combine regular expressions over surface tokens with regular expressions over graphs such as syntactic dependencies. To guarantee the rapid matching of these patterns, our framework indexes most of the necessary information for matching patterns, including directed graphs such as syntactic dependencies, into a custom Lucene index. Indexing minimizes the amount of expensive pattern matching that must take place at runtime. As a result, the runtime system matches a syntax-based graph traversal in 2.8 seconds in a corpus of over 134 million sentences, nearly 150,000 times faster than its predecessor.},
  url       = {https://www.aclweb.org/anthology/2020.lrec-1.267}
}
```  
{% endraw %}
