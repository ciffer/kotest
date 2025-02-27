@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.kotest.engine

import io.kotest.common.runBlocking
import io.kotest.common.runPromise
import io.kotest.core.TagExpression
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.config.ProjectConfiguration
import io.kotest.core.extensions.Extension
import io.kotest.core.project.TestSuite
import io.kotest.core.spec.Spec
import io.kotest.core.spec.SpecRef
import io.kotest.engine.config.ConfigManager
import io.kotest.engine.config.detectAbstractProjectConfigs
import io.kotest.engine.config.loadProjectConfigFromClassname
import io.kotest.engine.extensions.SpecifiedTagsTagExtension
import io.kotest.engine.listener.NoopTestEngineListener
import io.kotest.engine.listener.PinnedSpecTestEngineListener
import io.kotest.engine.listener.TeamCityTestEngineListener
import io.kotest.engine.listener.TestEngineListener
import io.kotest.engine.listener.ThreadSafeTestEngineListener
import io.kotest.mpp.log
import kotlin.reflect.KClass

/**
 * A builder class for creating and executing tests via the [TestEngine].
 *
 * Entry point for tests generated through the compiler plugins, and so the
 * public api cannot have breaking changes.
 */
class TestEngineLauncher(
   private val listener: TestEngineListener,
   private val projectConfiguration: ProjectConfiguration,
   private val configs: List<AbstractProjectConfig>,
   private val refs: List<SpecRef>,
   private val tagExpression: TagExpression?,
) {

   constructor() : this(
      NoopTestEngineListener,
      ProjectConfiguration(),
      emptyList(),
      emptyList(),
      null,
   )

   constructor(listener: TestEngineListener) : this(
      listener,
      ProjectConfiguration(),
      emptyList(),
      emptyList(),
      null,
   )

   /**
    * Convenience function to be called by the native code gen to set up the TeamCity listener.
    */
   fun withTeamCityListener(): TestEngineLauncher {
      return withListener(TeamCityTestEngineListener())
   }

   /**
    * Replace the listener with the given value.
    */
   fun withListener(listener: TestEngineListener): TestEngineLauncher {
      return TestEngineLauncher(
         listener = listener,
         projectConfiguration = projectConfiguration,
         configs = configs,
         refs = refs,
         tagExpression = tagExpression,
      )
   }

   fun withSpecs(vararg specs: Spec): TestEngineLauncher {
      return TestEngineLauncher(
         listener = listener,
         projectConfiguration = projectConfiguration,
         configs = configs,
         refs = specs.toList().map { SpecRef.Singleton(it) },
         tagExpression = tagExpression,
      )
   }

   fun withClasses(vararg specs: KClass<out Spec>): TestEngineLauncher = withClasses(specs.toList())
   fun withClasses(specs: List<KClass<out Spec>>): TestEngineLauncher {
      return TestEngineLauncher(
         listener = listener,
         projectConfiguration = projectConfiguration,
         configs = configs,
         refs = specs.toList().map { SpecRef.Reference(it) },
         tagExpression = tagExpression,
      )
   }

   /**
    * Adds a [AbstractProjectConfig] that was detected by the compiler plugin.
    */
   @Deprecated("Use withProjectConfig. Will be removed once compiler plugins are updated")
   fun withConfig(vararg projectConfig: AbstractProjectConfig): TestEngineLauncher {
      return withProjectConfig(*projectConfig)
   }

   /**
    * Adds a [AbstractProjectConfig] that was detected by the compiler plugin.
    */
   fun withProjectConfig(vararg projectConfig: AbstractProjectConfig): TestEngineLauncher {
      return TestEngineLauncher(
         listener = listener,
         projectConfiguration = projectConfiguration,
         configs = configs + projectConfig,
         refs = refs,
         tagExpression = tagExpression,
      )
   }

   fun withTagExpression(expression: TagExpression?): TestEngineLauncher {
      return TestEngineLauncher(
         listener = listener,
         projectConfiguration = projectConfiguration,
         configs = configs,
         refs = refs,
         tagExpression = expression,
      )
   }

   /**
    * Returns a copy of this launcher with the given [extensions] added to the configuration.
    *
    * Note: If after invoking this method, the [withConfiguration] is invoked, then any changes
    * here will be lost.
    */
   fun withExtensions(vararg extensions: Extension): TestEngineLauncher = withExtensions(extensions.toList())

   /**
    * Returns a copy of this launcher with the given [extensions] added to the configuration.
    *
    * Note: If after invoking this method, the [withConfiguration] is invoked, then any changes
    * here will be lost.
    */
   fun withExtensions(extensions: List<Extension>): TestEngineLauncher {
      extensions.forEach { projectConfiguration.registry.add(it) }
      return this
   }

   fun withConfiguration(configuration: ProjectConfiguration): TestEngineLauncher {
      return TestEngineLauncher(
         listener = listener,
         projectConfiguration = configuration,
         configs = configs,
         refs = refs,
         tagExpression = tagExpression,
      )
   }

   fun toConfig(): TestEngineConfig {

      // if the engine was configured with explicit tags, we register those via a tag extension
      tagExpression?.let { projectConfiguration.registry.add(SpecifiedTagsTagExtension(it)) }

      return TestEngineConfig(
         listener = ThreadSafeTestEngineListener(
            PinnedSpecTestEngineListener(
               listener
            )
         ),
         interceptors = testEngineInterceptors(),
         configuration = ConfigManager.initialize(
            projectConfiguration,
            configs + detectAbstractProjectConfigs() + listOfNotNull(loadProjectConfigFromClassname())
         ),
         tagExpression,
      )
   }

   fun testSuite(): TestSuite = TestSuite(refs)

   /**
    * Launch the [TestEngine] in an existing coroutine without blocking.
    */
   suspend fun async(): EngineResult {
      log { "TestEngineLauncher: Launching Test Engine" }
      val engine = TestEngine(toConfig())
      return engine.execute(testSuite())
   }

   /**
    * Launch the [TestEngine] created from this builder and block the thread until execution has completed.
    * This method will throw on JS.
    */
   fun launch(): EngineResult {
      log { "TestEngineLauncher: Launching Test Engine" }
      return runBlocking {
         val engine = TestEngine(toConfig())
         engine.execute(testSuite())
      }
   }

   /**
    * Launch the [TestEngine] created from this builder using a Javascript promise.
    * This method will throw on JVM or native.
    */
   fun promise() {
      log { "TestEngineLauncher: Launching Test Engine in Javascript promise" }
      runPromise {
         val engine = TestEngine(toConfig())
         engine.execute(testSuite())
      }
   }
}
