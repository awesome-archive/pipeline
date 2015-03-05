package org.allenai.pipeline

import org.allenai.common.Logging
import org.allenai.pipeline.IoHelpers._

import com.typesafe.config.Config
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat

import scala.reflect.ClassTag

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date

/** A fully-configured end-to-end pipeline
  * A top-level main can be constructed as:
  * object Foo extends App with Step1 with Step2 {
  * run(step1, step2)
  * }
  */
trait Pipeline extends Logging {
  def artifactFactory: FlatArtifactFactory[String] with StructuredArtifactFactory[String]

  def run(rawTitle: String, outputs: Producer[_]*): Unit = {
    try {
      require(
        outputs.forall(_.isInstanceOf[PersistedProducer[_, _]]),
        s"Running a pipeline without persisting the output: ${
          outputs.filter(p => !p
              .isInstanceOf[PersistedProducer[_, _]]).map(_.stepInfo.className).mkString(",")
        }}"
      )

      val start = System.currentTimeMillis
      outputs.foreach(_.get)
      val duration = (System.currentTimeMillis - start) / 1000.0
      logger.info(f"Ran pipeline in $duration%.3f s")

      val title = rawTitle.replaceAll( """\s+""", "-")
      val today = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())

      val workflowArtifact = artifactFactory.flatArtifact(s"summary/$title-$today.workflow.json")
      val workflow = Workflow.forPipeline(outputs: _*)
      SingletonIo.json[Workflow].write(workflow, workflowArtifact)

      val htmlArtifact = artifactFactory.flatArtifact(s"summary/$title-$today.html")
      SingletonIo.text[String].write(Workflow.renderHtml(workflow), htmlArtifact)

      val signatureArtifact = artifactFactory.flatArtifact(s"summary/$title-$today.signatures.json")
      val signatureFormat = Signature.jsonWriter
      val signatures = outputs.map(p => signatureFormat.write(p.stepInfo.signature)).toList.toJson
      signatureArtifact.write { writer => writer.write(signatures.prettyPrint)}

      logger.info(s"Summary written to ${toHttpUrl(htmlArtifact.url)}")
    } catch {
      case e: Throwable => logger.error("Untrapped exception", e)
    }
  }

  def persist[T, A <: Artifact : ClassTag](
    original: Producer[T],
    io: ArtifactIo[T, A],
    fileName: Option[String] = None,
    suffix: String = ""
  ) = {
    val path = fileName.getOrElse(
      s"${original.stepInfo.signature.name}.${original.stepInfo.signature.id}$suffix"
    )
    implicitly[ClassTag[A]].runtimeClass match {
      case c if c == classOf[FlatArtifact] => original.persisted(
        io,
        artifactFactory.flatArtifact(s"data/$path").asInstanceOf[A]
      )
      case c if c == classOf[StructuredArtifact] => original.persisted(
        io,
        artifactFactory.structuredArtifact(s"data/$path").asInstanceOf[A]
      )
      case _ => sys.error(s"Cannot persist using io class of unknown type $io")
    }
  }

  object Persist {

    object Iterator {
      def asText[T: StringSerializable : ClassTag](
        step: Producer[Iterator[T]],
        path: Option[String] = None,
        suffix: String = ""
      ): PersistedProducer[Iterator[T], FlatArtifact] =
        persist(step, LineIteratorIo.text[T], path, suffix)

      def asJson[T: JsonFormat : ClassTag](step: Producer[Iterator[T]],
        path: Option[String] = None,
        suffix: String = "")(
      ): PersistedProducer[Iterator[T], FlatArtifact] =
        persist(step, LineIteratorIo.json[T], path, suffix)
    }

    object Collection {
      def asText[T: StringSerializable : ClassTag](
        step: Producer[Iterable[T]],
        path: Option[String] = None,
        suffix: String = "")(
      ): PersistedProducer[Iterable[T], FlatArtifact] =
        persist(step, LineCollectionIo.text[T], path, suffix)

      def asJson[T: JsonFormat : ClassTag](
        step: Producer[Iterable[T]],
        path: Option[String] = None,
        suffix: String = "")(
      ): PersistedProducer[Iterable[T], FlatArtifact] =
        persist(step, LineCollectionIo.json[T], path, suffix)
    }

    object Singleton {
      def asText[T: StringSerializable : ClassTag](
        step: Producer[T],
        path: Option[String] = None,
        suffix: String = "")(
      ): PersistedProducer[T, FlatArtifact] =
        persist(step, SingletonIo.text[T], path, suffix)

      def asJson[T: JsonFormat : ClassTag](
        step: Producer[T],
        path: Option[String] = None,
        suffix: String = "")(
      ): PersistedProducer[T, FlatArtifact] =
        persist(step, SingletonIo.json[T], path, suffix)
    }

  }

  // Convert S3 URLs to an http: URL viewable in a browser
  def toHttpUrl(url: URI): URI = {
    url.getScheme match {
      case "s3" | "s3n" =>
        new java.net.URI("http", s"${
          url.getHost
        }.s3.amazonaws.com", url.getPath, null)
      case "file" =>
        new java.net.URI(null, null, url.getPath, null)
      case _ => url
    }
  }
}

trait ConfiguredPipeline extends Pipeline {
  def config: Config

  override lazy val artifactFactory = {
    val url = new URI(config.getString("output.dir"))
    val path = url.getPath.stripSuffix("/")
    val outputDirUrl = new URI(url.getScheme, url.getHost, path, null)
    ArtifactFactory.fromUrl(outputDirUrl)
  }

  def optionallyPersist[T, A <: Artifact : ClassTag](
    stepName: String,
    suffix: String = "")(
    original: Producer[T], io: ArtifactIo[T, A]
  ): Producer[T] = {

    val configKey = s"output.persist.$stepName"
    if (config.hasPath(configKey)) {
      config.getValue(configKey).unwrapped() match {
        case java.lang.Boolean.TRUE =>
          persist(original, io, None, suffix)
        case path: String =>
          persist(original, io, Some(path))
        case _ => original
      }
    } else {
      original
    }
  }
}