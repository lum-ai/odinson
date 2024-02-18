# Walkthrough Example

As an example of a typical usage of Odinson, imagine that you have a collection of text documents, from which you would like to extract mentions of pet adoptions. (A more cheerful example than, say, bombing events!)
For this example, we'll use a single text file:

```text
Sally loves dogs.  Yesterday, Sally adopted a cat named Ajax. 
```

## Step 1: Annotate the text

which is saved as `data/pets/text/text_1.txt`.  The next step is to annotate the text and build an Odinson index, both of which are offline steps.
For this, your config file (`extra/src/main/resources/application.conf`) should read:

```text 
 odinson.textDir = data/pets/text
 odinson.docDir = data/pets/docs
 odinson.indexDir = data/pets/index
```

To create the annotated Odinson documents, run this command from the project root directory:

    sbt "extra/runMain ai.lum.odinson.extra.AnnotateText"

The Odinson Document for the above text, created with one example Processor is here, but keep in mind that the fields you include in an Odinson Document are largely up to you!

```text
Document(6a2b13bf-515f-49fe-a4f3-b3a04aaf3bef,List(),ArraySeq(Sentence(4,List(TokensField(raw,WrappedArray(Sally, loves, dogs, .),true), TokensField(word,WrappedArray(Sally, loves, dogs, .),false), TokensField(tag,WrappedArray(NNP, VBZ, NNS, .),false), TokensField(lemma,WrappedArray(Sally, love, dog, .),false), TokensField(entity,WrappedArray(PERSON, O, O, O),false), TokensField(chunk,WrappedArray(B-NP, B-VP, B-NP, O),false), GraphField(dependencies,List((1,0,nsubj), (1,2,dobj), (1,3,punct)),Set(1),false))), Sentence(9,List(TokensField(raw,WrappedArray(Yesterday, ,, Sally, adopted, a, cat, named, Ajax, .),true), TokensField(word,WrappedArray(Yesterday, ,, Sally, adopted, a, cat, named, Ajax, .),false), TokensField(tag,WrappedArray(NN, ,, NNP, VBD, DT, NN, VBN, NNP, .),false), TokensField(lemma,WrappedArray(yesterday, ,, Sally, adopt, a, cat, name, Ajax, .),false), TokensField(entity,WrappedArray(DATE, O, PERSON, O, O, O, O, ORGANIZATION, O),false), TokensField(chunk,WrappedArray(B-NP, O, B-NP, B-VP, B-NP, I-NP, B-VP, B-NP, O),false), GraphField(dependencies,List((3,2,nsubj), (3,5,dobj), (3,8,punct), (3,0,nmod:tmod), (3,1,punct), (5,4,det), (5,6,acl), (6,7,xcomp)),Set(3),false)))))
```

## Step 2: Create the index
    
Then, to make the index, you can either run the provided app:

    sbt "extra/runMain ai.lum.odinson.extra.IndexDocuments"
    
Or, if customization is needed, you can use these steps in your own code:

```scala
import ai.lum.common.FileUtils._
import ai.lum.odinson.Document
import ai.lum.odinson.lucene.index.OdinsonIndexWriter

// Initialize the index writer
val writer = OdinsonIndexWriter.fromConfig()

// Gather the Document files to be indexed
val wildcards = Seq("*.json", "*.json.gz")
val files = new File("data/pets/docs").listFilesByWildcards(wildcards, recursive = true)

// Iterate through the files, and add each to the index
files.foreach(f => writer.addFile(f))

// Close the index
writer.close()
```


## Step 3: Use the index for queries

Once the index is built, we can query it to get the information we are interested in.
To do this, we first need to make an extractor engine, then pass it queries to apply.

```scala
import ai.lum.odinson.{EventMatch, ExtractorEngine, NamedCapture, OdinsonMatch}
import ai.lum.odinson.utils.DisplayUtils.displayMention

// Initialize the extractor engine -- ensure that your config still has `odinson.indexDir` pointing
// to where you wrote your index, here we were using data/pets/index
val extractorEngine = ExtractorEngine.fromConfig()

// Here we have a set of two rules, which will first find `Pet` mentions, and the find 
// `Adoption` Mentions.
val rules = """
    |rules:
    |  - name: pets_type
    |    type: basic
    |    label: Pet  # if found, will have the label "Pet"
    |    priority: 1 # will run in the first round of extraction
    |    pattern: |
    |       [lemma=/cat|dog|bunny|fish/]
    |
    |  - name: pets_adoption
    |    type: event
    |    label: Adoption
    |    priority: 2  # will run in the second round of extraction, can reference priority 1 rules
    |    pattern: |
    |      trigger = [lemma=adopt]
    |      adopter = >nsubj []   # note: we didn't specify the label, so any token will work
    |      pet: Pet = >dobj []
    """.stripMargin

// Compile the rules into Extractors that will be used with the Index
val extractors = extractorEngine.compileRuleString(rules)

// Extract Mentions
val mentions = extractorEngine.extractMentions(extractors)

// Display the mentions
mentions.foreach(displayMention(_, extractorEngine))
```

The output of the above code is:

```text
------------------------------
Mention Text: adopted
Label: Adoption
Found By: pets_adoption
  Trigger: adopted
  Args:
    * pet [Pet]: cat
    * adopter [no label]: Sally

------------------------------
Mention Text: dogs
Label: Pet
Found By: pets_type

------------------------------
Mention Text: cat
Label: Pet
Found By: pets_type
```



### Additional example usage

For an example usage, please see the complete working example [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/scala/ai/lum/odinson/extra/Example.scala).

To run it from the command line, use:

    sbt extra/run
     
and choose `Example` off the list.

To use it, you will need to point to an Odinson index by specifying the correct path in `application.conf`. If you need help making an index, or setting up your config file, there is info [here](https://github.com/lum-ai/odinson/tree/master/extra).

The file containing the Odinson rules being used in this example is [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/resources/example/rules.yml), and the output is a json lines file, with one line per extraction.  Please note that this example is meant to be illustrative only, and the output produced is not a true serialization of the extracted Mentions (i.e., only some attributes are included in the output). 
