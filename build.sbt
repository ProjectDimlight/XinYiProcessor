ThisBuild / scalaVersion := "2.12.13"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "XinYiProcessor"

lazy val root = (project in file("."))
  .settings(
    name := "xinyi",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.4.3",
      "edu.berkeley.cs" %% "chiseltest" % "0.3.3" % "test",
      "org.scalatest" %% "scalatest" % "3.2.5" % "test",
      "edu.berkeley.cs" %% "chisel-iotesters" % "1.5.2"
    ),
    scalacOptions ++= Seq(
      "-Xsource:2.11",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.4.3" cross CrossVersion.full),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )
