package ai.lum.odinson.taxonomy

import ai.lum.odinson.state.State.TAXONOMY_CONFIG_PATH
import ai.lum.odinson.test.utils.OdinsonTest
import com.typesafe.config.{Config, ConfigValueFactory}

class TestTaxonomyMatching extends OdinsonTest {


  behavior of "TaxonomyAware Engine"

  it should "match hypernyms of requested prev found mentions" in {
    val rules = """
                  |rules:
                  |  - name: bear_rule
                  |    type: basic
                  |    label: Bear
                  |    priority: 1
                  |    pattern: |
                  |      bears
                  |
                  |  - name: animal_rule
                  |    type: event
                  |    label: Eat
                  |    priority: 2
                  |    pattern: |
                  |       trigger = [lemma=eat]
                  |       obj: Animal = >dobj []
    """.stripMargin

    val customConfig: Config = defaultConfig.withValue(
      TAXONOMY_CONFIG_PATH,
      ConfigValueFactory.fromAnyRef("/testGrammar/testTaxonomy.yml")
    )
    val ee = mkMemoryExtractorEngine(customConfig, getDocument("becky-gummy-bears-v2"))

    val mentions = ee.extractMentions(ee.compileRuleString(rules)).toArray
    getMentionsWithLabel(mentions, "Eat") should have length(1)

  }


}
