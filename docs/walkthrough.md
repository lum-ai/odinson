---  
title: Walkthrough Example
parent: Getting Started
has_children: false  
nav_order: 1  
---  

## Walkthrough Example

As an example of a typical usage of Odinson, imagine that you have a collection of text documents, from which you would like to extract mentions of pet adoptions. (A more cheerful example than, say, bombing events!)
For this example, we'll use a single text file:

```text
Sally loves dogs.  In fact, she owns two dogs.
Yesterday, though, Sally adopted a cat named Ajax.
The same day, Ricardo adopted a terrier and a pug.
It was a good day for the shelter! 
```

which is saved as `data/pets/text/text_1.txt`.  The next step is to annotate the text and build an Odinson index.

```scala


```

Once the index is built, we can query it to get the information we are interested in.

For example, let's write a grammar that first looks for occurrences of pets (here using a small set of keywords), and then we'll write a rule that finds mentions of pet adoptions.

We'll keep these rules in a rule file, such as rules_pets.yml:

```yaml
rules:
  - name: pets_type
    type: basic
    label: Pet  # if found, will have the label "Pet"
    priority: 1 # will run in the first round of extraction
    pattern: |
       [lemma=/cat|dog|bunny|fish/]

  - name: pets_breed
    type: basic
    label: Pet
    priority: 1 
    pattern: |
       [lemma=/terrier|pug/]
    
  - name: pets_adoption
    type: event
    label: Adoption
    priority: 2  # will run in the second round of extraction, can reference priority 1 rules
    pattern: |
      trigger = [lemma=adopt]
      adopter = >nsubj []   # note: we didn't specify the label, so any token will work
      pet: Pet = >dobj []
```

Now, we'll use this rule file to extract the information of interest, for example:

```scala


```







For an example usage, please see the complete working example [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/scala/ai/lum/odinson/extra/Example.scala).

To run it from the command line, use:

    sbt extra/run
     
and choose `Example` off the list.

To use it, you will need to point to an Odinson index by specifying the correct path in `application.conf`. If you need help making an index, or setting up your config file, there is info [here](https://github.com/lum-ai/odinson/tree/master/extra).

The file containing the Odinson rules being used in this example is [here](https://github.com/lum-ai/odinson/blob/master/extra/src/main/resources/example/rules.yml), and the output is a json lines file, with one line per extraction.  Please note that this example is meant to be illustrative only, and the output produced is not a true serialization of the extracted Mentions (i.e., only some attributes are included in the output). 
