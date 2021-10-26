package ai.lum.odinson.test.utils

import javax.inject._

import com.google.inject.AbstractModule
import com.google.inject.Provides

import ai.lum.odinson.ExtractorEngine

class OdinsonProviderModule extends AbstractModule {

  override def configure() {}

  @Provides @Singleton
  def extractorEngineProvider(): ExtractorEngine = ExtractorEngine.fromConfig()

}
