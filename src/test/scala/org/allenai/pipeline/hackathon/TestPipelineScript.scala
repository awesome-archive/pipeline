package org.allenai.pipeline.hackathon

import java.io.File

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.pipeline.hackathon.PipelineScript._

import scala.io.Source

class TestPipelineScript extends UnitSpec {
  "pipeline scripting" should "successfully parse a step command" in {
    val program = """python {in:"$scripts/ExtractArrows.py"} -i {in:"./png", id:"pngDir"} -o {out:"arrowDir", type:"dir"}"""
    val parser = new PipelineScript.Parser
    val parsed = parser.parseAll(parser.stepStatement, program)
    assert(parser.parseAll(parser.stepStatement, program).successful)
  }

  ignore should "successfully parse a small sample program" in {
    val simpleProgram =
      """|package {source: "./scripts", id: "scripts"}
         |
         |# Woohoo
         |{in:"$scripts/asdf"} eek {out:"$scripts/asdf"}""".stripMargin

    val parser = new PipelineScript.Parser
    val parsed = parser.parseText(simpleProgram).toSeq
    assert(parsed === Seq(
      PackageStatement(List(Arg("source", "./scripts"), Arg("id", "scripts"))),
      CommentStatement("# Woohoo"),
      StepStatement(List(ArgToken(Seq(Arg("in", "$scripts/asdf"))), StringToken("eek "), ArgToken(Seq(Arg("out", "$scripts/asdf")))))
    ))
  }

  ignore should "successfully parse the sample vision workflow" in {
    val resourceUrl = {
      val url = this.getClass.getResource("/pipeline/vision-workflow.pipe")
      require(url != null, "Could not find resource.")
      url
    }
    val visionWorkflow = Source.fromURL(resourceUrl).getLines.toList

    val parser = new PipelineScript.Parser
    val parsed = parser.parseLines(visionWorkflow).toSeq

    assert(parsed.size > 0)
  }

  ignore should "build a pipeline from a script" in {
    val resourceUrl = {
      val url = this.getClass.getResource("/pipeline/vision-workflow.pipe")
      require(url != null, "Could not find resource.")
      url
    }
    val visionWorkflow = Source.fromURL(resourceUrl).getLines.toList

    val parser = new PipelineScriptParser()
    val script = parser.parseLines(null)(visionWorkflow)
    println(script)
  }

  it should "run a pipeline from a script" in {
    val resourceUrl = {
      val url = this.getClass.getResource("/pipeline/vision-workflow.pipe")
      require(url != null, "Could not find resource.")
      url
    }
    val visionScriptLines = Source.fromURL(resourceUrl).getLines.toList

    val dir = new File(new File("pipeline-output"), "RunScript").toURI
    val pipeline = WorkflowScriptPipeline.buildPipeline(dir, visionScriptLines)
    pipeline.run("RunFromScript")
  }
}
