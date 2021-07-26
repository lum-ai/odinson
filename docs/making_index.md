---  
title: Creating an Index  
parent: Getting Started
has_children: false  
nav_order: 3
---  
  
  
# Indexing Odinson Documents  
Once you have [created Odinson Documents](documents.html), you can create an Odinson index, the data structure that Odinson uses for executing queries.  
  
## Configuration  
Once again, you will specify the configurations in `extra/src/main/resources/application.conf`.  
  
- Ensure `odinson.docDir` directory contains annotated `ai.lum.odinson.Document`s (`.json` or `.json.gz`).  
- Ensure `odinson.indexDir` is pointing to where you want to create the index.  
- Note again, if you are using the typical directory structure (see above), you can simply ensure that `odinson.dataDir = path/to/your/dataDir` and the other paths will be correct.  
  
## Command  
  
	sbt "extra/runMain ai.lum.odinson.extra.IndexDocuments"    
  
While annotating can be time-consuming, the creation of the index should be relatively less so, though again it's dependent on the number and size of the documents.