package com.sksamuel.kotest.factory

import io.kotest.core.spec.FunSpec
import io.kotest.core.spec.funSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

private val factory = funSpec {

   var before = false
   var after = false

   beforeTest {
      before = false
   }

   afterTest {
      after = false
   }

   test("checking that before was called and after was not yet") {
      before.shouldBeTrue()
      after.shouldBeFalse()
   }

   test("checking that after was called") {
      after.shouldBeTrue()
   }
}

class TestFactoryCallbackTest : FunSpec() {
   init {
      include(factory)
   }
}
