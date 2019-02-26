# Odinson

Odinson can be used to rapidly query a natural language knowledge base and extract structured relations. Query patterns can be designed over (a) surface (e.g. #1), syntax (e.g., #2), or a combination of both (e.g., #3-5). These examples were executed over a collection of 8,479 scientific papers, corresponding to 1,105,737 sentences. Please note that the rapidity of the execution allows a user to dynamically develop these queries in _real-time_, immediately receiving feedback on the coverage and precision of the patterns at scale.

## Project overview

Odinson supports several features:

- Patterns over tokens, including boolean patterns over token features like lemma, POS tags, NER tags, chunk tags, etc
- Patterns over syntax by matching paths in a dependency graph. Note that this actually agnostic to the tags in the graph edges and it could be repurposed for matching over semantic roles or something else.
- Named captures for extracting the different entities involved in a relation

And there are many more on the way:

- Testing framework and extensive test suite
- Better error messages
- Better support for greedy and lazy quantifiers
- Lookaround assertions
- Support for an internal state to hold intermediate mentions, allowing for the application of patterns in a cascade
- Support for grammars (similar to odin)
- Filtering results by document metadata (e.g., authors, publication date)

We would also love to hear any questions, requests, or suggestions you may have.

It consists of several subprojects:

- core: the core odinson library
- extra: these are a few apps that we need but don't really belong in core,
        for example, licensing issues
- backend: this is a REST API for odinson
- ui: this is a webapp that we are building to interact with the system and visualize results

The three apps in extra are:

- AnnotateText: parses text documents using processors
- IndexDocuments: reads the parsed documents and builds an odinson index
- Shell: this is a shell where you can execute queries (we will replace this with the webapp soon)

## Examples

We have made a few example queries to show how the system works. For this we used a collection of 8,479 scientific papers (or 1,105,737 sentences). Please note that the rapidity of the execution allows a user to dynamically develop these queries in real-time, immediately receiving feedback on the coverage and precision of the patterns at scale.

### Example of a surface pattern for extracting casual relations.

This example shows odinson applying a pattern over surface features (i.e., words) to extract mentions of causal relations. Note that Odinson was able to find 3,774 sentences that match the pattern in 0.17 seconds.

![example 1](https://github.com/lum-ai/odinson/raw/dev/images/image1.png "example 1")




### Example of a doubly-anchored Hearst pattern to extract hypernymy (i.e., _X_ isA _Y_)

This example shows how Odinson can also use patterns over syntax. In this case it tries to find hypernym relations. It finds 10,562 matches in 0.39 seconds.

    >>> (?<src>[]) >nmod_such_as (?<dst>[]) >conj (?<dst>[])
    found 10,562 matches in 0.39 seconds
    showing 1 to 5

    Doc 10.1186/s12014-017-9166-9 (score = 16.948864)
    Glycoproteins , such as DSC3 , DSG3 , PLOD2 , DSC2 , VCAN , PLOD1 , DSG2 , SLC2A1 , TIMP1 , and EGFR were increased in SqCC .

    Doc 10.4103/2225-4110.119708 (score = 16.948864)
    The FAO boasts that insects contain significant amounts of vital nutrients and minerals such as protein , fiber , calcium , copper , iron , magnesium , manganese , phosphorus , selenium , and zinc .

    Doc 10.1186/1746-1596-6-127 (score = 16.948864)
    The tumor cells were negative for other markers , such as CD117 , SM-actin , S-100 , CD68 , Myo-D1 , CD99 , Desmin , CD31 , Calretinin , Synaptophysin , Cytokeratin , and Keratin ( Figure 6 ) .

    Doc 10.4103/2225-4110.107700 (score = 16.716448)
    [ 102103104105 ] Similarly , EGCG from green tea epigenetically controls several molecular cancer targets such as RAR beta , hTERT , GSTP1 , p16 , MGMT , hMLH1 , MAGE-A1 , Alu , LINE , BCLl2 , IL-6 , IL-12 , NF-kappa B , and NOS-2 by DNA methylation , chromatin modification , and miRNA regulation .

    Doc 10.1186/1746-1596-6-126 (score = 16.716448)
    Some proteins ' alteration was found in thyroid cancer , such as CK19 , TG , Ki67 , Calcitonin , TTF-1 , BRAF , RET , HBME-1 , SERPINA1 , TfR1 and CD71 , FHL1 and galectin-3 [ 3-12 ] .





### Example of how a surface pattern can be extended (using syntax patterns) to extract additional contextual information.

This example shows how surface and syntax can be combined in a single pattern. This pattern finds 12 sentences that match in our corpus of 1,105,737 sentences. It does this in 0.05 seconds.

    >>> (?<cause> []) leads to (?<effect> []) >nmod_during (?<context> [])
    found 12 matches in 0.05 seconds
    showing 1 to 5

    Doc 10.1186/1746-1596-5-81 (score = 15.870108)
    During pregnancy , the physiological augmentation of minute ventilation leads to a greater dust burden into lung parenchyma .

    Doc 10.1186/1746-1596-6-128 (score = 15.870108)
    During pregnancy , adhesion of P. falciparum infected erythrocytes to syncytiotrophoblast leads to parasite sequestration in the intervillous space .

    Doc 10.1186/1746-1596-6-83 (score = 15.870108)
    During pregnancy , adhesion of Plasmodium falciparum infected erythrocytes to syncytiotrophoblast leads to parasite sequestration in the intervillous space .

    Doc 4298363 (score = 15.870108)
    During aging , cumulative molecular damage leads to impairment and functional decline [ 13 , 14 ] .

    Doc 10.1186/1746-1596-8-189 (score = 14.149792)
    During pregnancy , adhesion of Plasmodium falciparum infected erythrocytes to the syncytiotrophoblast leads to parasite sequestration in the intervillous space .





### Example of a causal pattern written over dependency syntax with a lexical trigger (i.e., _cause_).

This example shows how we can match over different aspects of tokens, lemmas in this example. Odinson finds 5,489 matches in 0.17 seconds.

    >>> (?<cause> []) <nsubj [lemma=cause] >dobj (?<effect> [])
    found 5,489 matches in 0.17 seconds
    showing 1 to 5

    Doc 10.1007/s00216-011-5162-5 (score = 12.486847)
    Aldosterone , betamethasone , corticosterone , cortisol , Dex and prednisolone caused a dose related increase in luciferase activity , whilst cortisone and prednisone showed no response .

    Doc 3615154 (score = 11.748337)
    For example , taken together , hyperfunctions of arterial smooth muscle cells , macrophages , hepatocytes , fat cells , blood platelets , neurons and glial cells , fibroblasts , beta-cells cause organ hypertrophy and fibrosis , atherosclerosis and hypertension ( and their complications such as stroke and infarction ) , osteoporosis and ( as complication , born rupture ) , age related blindness , gangrenes , renal and heart failure and even cancer .

    Doc 10.1186/1471-2482-13-S2-S46 (score = 11.159743)
    Vascular insult or disease causes the up-regulation of the hypoxia inducible factor alpha ( HIF alpha ) , a transcription factor driving the local expression of the cytokines - VEGF , SDF-alpha , and erythropoietin ( EPO ) - that stimulate EPC release from the stem cell niche into peripheral blood ( PB ) .

    Doc 10.18632/aging.101108 (score = 11.101239)
    Ni may cause neurotoxicity , hepato-toxicity , nephrotoxicity , gene toxicity , reproductive toxicity , and increased risk of cancer [ 1 , 4 ] .

    Doc 4276790 (score = 11.101239)
    Both metal overload and deficiencies can cause metabolic defects , cell cycle arrest and cell death leading to severe pathologies [ 1-4 ] .





### Example of how more complex patterns can be developed, for example, to extract the polarity of a causal influence and a context in which it applies.

This is an example of a slightly more complex pattern. Odinson is able to apply it over our corpus and finds 228 matches in 0.06 seconds.

    >>> (?<cause> []) <nsubj [lemma=cause] >dobj (?<increase> [lemma=increase]) >nmod_in (?<theme> [])
    found 228 matches in 0.06 seconds
    showing 1 to 5

    Doc 3117459 (score = 20.717516)
    Health care progress , antibiotics , vaccination and improving life standards caused a dramatic increase in lifespan in recent decades .

    Doc 10.1007/s00216-011-5162-5 (score = 19.85542)
    Aldosterone , betamethasone , corticosterone , cortisol , Dex and prednisolone caused a dose related increase in luciferase activity , whilst cortisone and prednisone showed no response .

    Doc 10.1007/s00216-011-5162-5 (score = 19.403738)
    Aldosterone , betamethasone , budesonide , corticosterone , cortisol , dexamethasone and prednisolone caused a dose related increase in the production of yEGFP , demonstrating that these compounds are potent glucocorticoids .

    Doc 10.18632/aging.100939 (score = 18.329927)
    We found that heat stress , osmotic stress , oxidative stress and cold stress could cause an increase in lifespan ( Fig S19 ) .

    Doc 10.1007/s00216-007-1559-6 (score = 17.02212)
    5 alpha-Dihydrotestosterone , 17 beta-testosterone and 17 beta-boldenone caused a dose related increase in the production of yEGFP , demonstrating that these compounds are potent androgens .





## Features

Odinson currently supports:

- Patterns over tokens, including boolean patterns over token features like lemma, POS tags, NER tags, chunk tags, etc
- Patterns over syntax by matching paths in a dependency graph. Note that this actually agnostic to the tags in the graph edges and it could be repurposed for matching over semantic roles or something else.
- Named captures for extracting the different entities involved in a relation

### TODO

These are the main features that are in our radar:

- Testing framework and extensive test suite
- Better error messages
- Better support for greedy and lazy quantifiers
- Lookaround assertions
- Support for an internal state to hold intermediary mentions, allowing for the application of patterns in a cascade
- Support for grammars (similar to odin)
- Filtering results by document metadata (e.g., authors, publication date)
