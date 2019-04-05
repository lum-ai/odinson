/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.lum.odinson.utils

import java.io.File
import java.util.Properties
import java.util.{ Map => JMap }
import scala.collection.JavaConverters._
import com.typesafe.config._
import com.typesafe.config.{ ConfigFactory => TypesafeConfigFactory }
import com.typesafe.config.impl.ConfigImpl

// TODO move to ai.lum.common

object ConfigFactory {

  /**
   * Returns an empty Configuration object.
   */
  def empty = TypesafeConfigFactory.empty()

  /**
   * Returns the reference configuration object.
   */
  def reference = TypesafeConfigFactory.defaultReference()

  /**
   * Create a new Configuration from the given key-value pairs.
   */
  def from(data: (String, Any)*): Config = from(data.toMap)

  /**
   * Create a new Configuration from the data passed as a Map.
   */
  def from(data: Map[String, Any]): Config = {

    def toJava(data: Any): Any = data match {
      case map: Map[_, _]        => map.mapValues(toJava).toMap.asJava
      case iterable: Iterable[_] => iterable.map(toJava).asJava
      case v                     => v
    }

    TypesafeConfigFactory.parseMap(toJava(data).asInstanceOf[JMap[String, Any]])

  }

  /**
   * Create a new Configuration from the given properties.
   */
  def from(properties: Properties): Config = {
    // Iterating through the system properties is prone to ConcurrentModificationExceptions
    // Typesafe config maintains a cache for this purpose.  So, if the passed in properties *are* the system
    // properties, use the Typesafe config cache, otherwise it should be safe to parse it ourselves.
    if (properties eq System.getProperties) {
      ConfigImpl.systemPropertiesAsConfig()
    } else {
      TypesafeConfigFactory.parseProperties(properties)
    }
  }

  /**
   * Load a new Configuration.
   */
  def load(
    classLoader: ClassLoader = getClass().getClassLoader(),
    properties: Properties = System.getProperties(),
    directSettings: Map[String, Any] = Map.empty,
    allowMissingApplicationConf: Boolean = true
  ): Config = {

    // Get configuration from the system properties.
    val systemPropertyConfig = from(properties)

    // Inject our direct settings into the config.
    val directConfig: Config = from(directSettings)

    // Resolve application.conf ourselves because:
    // - we may want to load configuration when application.conf is missing.
    // - We also want to delay binding and resolving reference.conf, which
    //   is usually part of the default application.conf loading behavior.
    // - We want to read config.file and config.resource settings from our
    //   own properties and directConfig rather than system properties.
    val applicationConfig: Config = {
      def setting(key: String): Option[Any] = {
        directSettings.get(key).orElse(Option(properties.getProperty(key)))
      }
      {
        setting("config.resource").map(resource => TypesafeConfigFactory.parseResources(classLoader, resource.toString))
      }.orElse {
        setting("config.file").map(fileName => TypesafeConfigFactory.parseFileAnySyntax(new File(fileName.toString)))
      }.getOrElse {
        val parseOptions = ConfigParseOptions.defaults
          .setClassLoader(classLoader)
          .setAllowMissing(allowMissingApplicationConf)
        TypesafeConfigFactory.defaultApplication(parseOptions)
      }
    }

    // Resolve reference.conf ourselves because ConfigFactory.defaultReference resolves values.
    val referenceConfig: Config = TypesafeConfigFactory.parseResources(classLoader, "reference.conf")

    // Combine all the config together into one big config
    val combinedConfig: Config = Seq(
      systemPropertyConfig,
      directConfig,
      applicationConfig,
      referenceConfig
    ).reduceLeft(_.withFallback(_))

    // Resolve settings. The `odinson.dataDir` setting will be substituted
    // into the default settings in referenceConfig.
    val resolvedConfig = combinedConfig.resolve

    resolvedConfig

  }

}
