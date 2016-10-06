/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.exercises
package services

import org.scalaexercises.runtime.{ Exercises, Timestamp }
import org.scalaexercises.runtime.model.{ DefaultLibrary, BuildInfo ⇒ RuntimeBuildInfo, Contribution ⇒ RuntimeContribution, Exercise ⇒ RuntimeExercise, Library ⇒ RuntimeLibrary, Section ⇒ RuntimeSection }
import org.scalaexercises.types.exercises._
import play.api.Play
import play.api.Logger
import cats.data.{ State, Xor }
import cats.std.list._
import cats.std.option._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.syntax.traverse._
import cats.syntax.xor._
import org.scalaexercises.types.evaluator.Dependency
import org.apache.commons.io.IOUtils
import sun.misc.BASE64Encoder

object ExercisesService extends RuntimeSharedConversions {

  def classLoader = Play.maybeApplication.fold(ExercisesService.getClass.getClassLoader)(_.classloader)
  lazy val (errors, runtimeLibraries) = Exercises.discoverLibraries(cl = classLoader)

  lazy val (libraries: List[Library], librarySections: Map[String, List[Section]]) = {
    val libraries1 = colorize(runtimeLibraries).map(convertLibrary)
    errors.foreach(error ⇒ Logger.warn(s"$error")) // TODO: handle errors better?
    (libraries1, libraries1.map(lib ⇒ lib.name → lib.sections).toMap)
  }

  lazy val base64Encoder = new BASE64Encoder()

  def section(libraryName: String, name: String): Option[Section] =
    librarySections.get(libraryName) >>= (_.find(_.name == name))

  def buildRuntimeInfo(evaluation: ExerciseEvaluation): ExerciseEvaluation.EvaluationRequest = {

    val runtimeInfo: Xor[String, (RuntimeBuildInfo, String, List[String])] = for {

      runtimeLibrary ← runtimeLibraries.find(_.name == evaluation.libraryName)
        .toRightXor(s"Unable to find library ${evaluation.libraryName} when " +
          s"attempting to evaluate method ${evaluation.method}")

      runtimeSection ← runtimeLibrary.sections.find(_.name == evaluation.sectionName)
        .toRightXor(s"Unable to find section ${evaluation.sectionName} when " +
          s"attempting to evaluate method ${evaluation.method}")

      runtimeExercise ← runtimeSection.exercises.find(_.qualifiedMethod == evaluation.method)
        .toRightXor(s"Unable to find exercise for method ${evaluation.method}")

    } yield (runtimeLibrary.buildMetaInfo, runtimeExercise.packageName, runtimeExercise.imports)

    runtimeInfo match {
      case Xor.Right((b: RuntimeBuildInfo, pckgName: String, importList: List[String])) ⇒

        val (resolvers, dependencies, code) = Exercises.buildEvaluatorRequest(
          pckgName,
          evaluation.method,
          evaluation.args,
          importList,
          b.resolvers,
          b.libraryDependencies
        )

        (
          resolvers,
          dependencies map (d ⇒ Dependency(d.groupId, d.artifactId, d.version)),
          code
        ).right

      case Xor.Left(msg) ⇒ msg.left
    }
  }

  def reorderLibraries(topLibNames: List[String], libraries: List[Library]): List[Library] = {
    val topLibIndex = topLibNames.zipWithIndex.toMap
    libraries.sortBy(lib ⇒ topLibIndex.getOrElse(lib.name, Integer.MAX_VALUE))
  }
}

sealed trait RuntimeSharedConversions {
  // not particularly clean, but this assigns colors
  // to libraries that don't have a default color provided
  // TODO: make this nicer
  def colorize(libraries: List[RuntimeLibrary]): List[RuntimeLibrary] = {
    type Colors = List[String]
    val autoPalette: Colors = List(
      "#00587A",
      "#44BBFF",
      "#EBF680",
      "#66CC99",
      "#FCA65F",
      "#112233",
      "#FC575E",
      "#CDCBA6",
      "#37465D",
      "#DD6F47",
      "#6AB0AA",
      "#008891",
      "#0F3057"
    )

    libraries.traverseU { library ⇒
      if (library.color.isEmpty) {
        State[Colors, RuntimeLibrary] { colors ⇒
          val (color, colors0) = colors match {
            case head :: tail ⇒ Some(head) → tail
            case Nil          ⇒ None → Nil
          }
          val library0 = DefaultLibrary(
            owner = library.owner,
            repository = library.repository,
            name = library.name,
            description = library.description,
            color = color,
            logoPath = library.logoPath,
            logoData = library.logoData,
            sections = library.sections,
            timestamp = Timestamp.fromDate(new java.util.Date()),
            buildMetaInfo = library.buildMetaInfo
          )
          (colors0, library0)
        }
      } else {
        State.pure[Colors, RuntimeLibrary](library)
      }
    }.runA(autoPalette).value
  }

  def convertLibrary(library: RuntimeLibrary): Library =
    Library(
      owner = library.owner,
      repository = library.repository,
      name = library.name,
      description = library.description,
      color = library.color getOrElse "black",
      logoPath = library.logoPath,
      logoData = loadLogoSrc(library.logoPath),
      sections = library.sections map convertSection,
      timestamp = library.timestamp,
      buildInfo = convertBuildInfo(library.buildMetaInfo)
    )

  def loadLogoSrc(logoPath: String): Option[String] = {
    def checkLogoPath(): Boolean =
      Option(getClass.getClassLoader.getResource(logoPath + ".svg")).isDefined

    def logoData(path: String): Option[String] =
      Option(getClass().getClassLoader.getResourceAsStream(path))
        .map(stream ⇒ ExercisesService.base64Encoder.encode(IOUtils.toByteArray(stream)))

    val logoPathOrDefault = if (checkLogoPath()) logoPath + ".svg" else "public/images/library_default_logo.svg"
    logoData(logoPathOrDefault)
  }

  def convertBuildInfo(buildInfo: RuntimeBuildInfo): BuildInfo =
    BuildInfo(
      resolvers = buildInfo.resolvers,
      libraryDependencies = buildInfo.libraryDependencies
    )

  def convertSection(section: RuntimeSection): Section =
    Section(
      name = section.name,
      description = section.description,
      path = section.path,
      exercises = section.exercises.map(convertExercise),
      contributions = section.contributions.map(convertContribution)
    )

  def convertExercise(exercise: RuntimeExercise): Exercise =
    Exercise(
      method = exercise.qualifiedMethod,
      name = Option(exercise.name),
      description = exercise.description,
      code = Option(exercise.code),
      explanation = exercise.explanation
    )

  def convertContribution(contribution: RuntimeContribution): Contribution =
    Contribution(
      sha = contribution.sha,
      message = contribution.message,
      timestamp = contribution.timestamp,
      url = contribution.url,
      author = contribution.author,
      authorUrl = contribution.authorUrl,
      avatarUrl = contribution.avatarUrl
    )

}
