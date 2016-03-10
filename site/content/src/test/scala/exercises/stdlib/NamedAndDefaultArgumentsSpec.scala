package exercises

import stdlib._
import shapeless.HNil

import org.scalatest.Spec
import org.scalatest.prop.Checkers

// FIXME: get rid of this if possible
import org.scalacheck.Shapeless._

class NamedandDefaultArgumentsSpec extends Spec with Checkers {
  val Arguments = NamedandDefaultArguments

  def `class without parameters` = {
    check(
      Test.success(
        Arguments.classWithoutParametersNamedandDefaultArguments _,
        255 :: 0 :: 0 :: HNil
      )
    )
  }

  def `default arguments` = {
    check(
      Test.success(
        Arguments.defaultArgumentsNamedandDefaultArguments _,
        0 :: 255 :: 0 :: HNil
      )
    )
  }

  def `arguments in any order` = {
    check(
      Test.success(
        Arguments.anyOrderNamedandDefaultArguments _,
        100 :: 100 :: 100 :: HNil
      )
    )
  }

  def `access to class parameters` = {
    check(
      Test.success(
        Arguments.accessClassParametersNamedandDefaultArguments _,
        10 :: 90 :: 30 :: HNil
      )
    )
  }

  def `parameters in class definition` = {
    check(
      Test.success(
        Arguments.defaultClassArgumentsNamedandDefaultArguments _,
        0 :: 325 :: 100 :: HNil
      )
    )
  }

  def `functional default parameters` = {
    check(
      Test.success(
        Arguments.functionalDefaulParametersNamedandDefaultArguments _,
        10 :: 25 :: HNil
      )
    )
  }
}