package org.allenai.pipeline.hackathon

import org.allenai.common.testkit.UnitSpec
import org.allenai.pipeline.hackathon.PipescriptParser._

class TestPipescriptParser extends UnitSpec {
  "variable resolution" should "work with curly braces" in {
    val environment = collection.mutable.Map("x" -> "foo")
    val s = SubstitutionString("\"y = ${x}\"")
    assert(s.resolve(environment).asString === "y = foo")
  }

  it should "work without curly braces" in {
    val environment = collection.mutable.Map("x" -> "foo")
    val s = new SubstitutionString("\"y = $x\"")
    assert(s.resolve(environment).asString === "y = foo")
  }

  "pipeline scripting" should "successfully parse a step command" in {
    val program =
      """run python {in:"$scripts/ExtractArrows.py"} -i {in:"./png", id:"pngDir"} -o {out:"arrowDir", type:"dir"}""".stripMargin
    val parser = new PipescriptParser.Parser
    val parsed = parser.parseAll(parser.script, program)
    assert(parsed.successful)
  }

  it should "successfully parse a variable command" in {
    val program = """set {x: "foo"}"""
    val parser = new PipescriptParser.Parser
    val parsed = parser.parseAll(parser.variableStatement, program)
    assert(parsed.successful)
  }

  /*
  it should "successfully parse and use a variable command" in {
    val program =
      """set x = foo
        |echo {in: "$x"}
      """.stripMargin
    val parser = new PipescriptParser.Parser
    val parsed = parser.parseText(program).toSeq
    assert(parsed.length === 2)
    assert(parsed(1).isInstanceOf[StepStatement])
    assert(parsed(1).asInstanceOf[StepStatement].tokens(1).asInstanceOf[ArgToken].args.find(_
        .name == "in").get.value === "foo")
  }
  */

  it should "successfully parse a small sample program" in {
    val simpleProgram =
      """| package {source: "./scripts", id: "scripts"}
        |
        |# Woohoo
        |run {input:"asdf",
        |     ignore:"false"} `run`
        |     {output:"fdsa"}
        |
        |run echo done""".stripMargin

    val parser = new PipescriptParser.Parser
    val parsed = parser.parseScript(simpleProgram).toList

    assert(parsed(0) === PackageStatement(Block(Seq(
      Arg("source", SimpleString.from("./scripts")),
      Arg("id", SimpleString.from("scripts"))
    ))))

    assert(parsed(1).isInstanceOf[CommentStatement])

    assert(parsed(2) === StepStatement(List(
      ArgToken(Block(List(Arg("input", SimpleString.from("asdf")), Arg("ignore", SimpleString.from("false"))))),
      StringToken("run"),
      ArgToken(Block(List(Arg("output", SimpleString.from("fdsa")))))
    )))

    assert(parsed(3) === StepStatement(List(
      StringToken("echo"), StringToken("done")
    )))
  }
}
